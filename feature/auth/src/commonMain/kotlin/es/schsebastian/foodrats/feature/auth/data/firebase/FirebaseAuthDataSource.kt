package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.session.LocalDataEraser
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.auth.data.apple.AppleSignInToken
import es.schsebastian.foodrats.feature.auth.data.google.GoogleIdToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class FirebaseAuthDataSource(
    private val auth: FirebaseAuth,
    private val store: AccountDocStore,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val localDataEraser: LocalDataEraser,
) {
    private val healer = AccountDocSelfHealer(
        store = store,
        clock = clock,
        googleName = { auth.currentUser?.displayName.orEmpty() },
    )

    // Revoked-session sign-outs bypass AuthSignOutPort (the voluntary funnel that wipes
    // account-scoped local data), so they must scrub the device themselves — see the class KDoc.
    private val revokedCleanup = RevokedSessionCleanup(
        signOut = { auth.signOut() },
        localDataEraser = localDataEraser,
    )

    suspend fun signInWithGoogle(token: GoogleIdToken): String = withContext(dispatchers.io) {
        val cred = GoogleAuthProvider.credential(idToken = token.raw, accessToken = token.accessToken)
        auth.signInWithCredential(cred).user?.uid ?: error("Firebase Auth returned null user")
    }

    suspend fun signInWithApple(token: AppleSignInToken): String = withContext(dispatchers.io) {
        // OAuthProvider.credential mints an Apple credential from the identity-token JWT + the RAW
        // (un-hashed) nonce — Firebase re-hashes and matches it against the nonce Apple signed into
        // the token. accessToken is intentionally null so the iOS GitLive impl routes to the
        // idToken+rawNonce native overload (see GoogleAuthProvider.credential for the parallel).
        val cred = OAuthProvider.credential(
            providerId = "apple.com",
            idToken = token.identityToken,
            rawNonce = token.nonce,
        )
        auth.signInWithCredential(cred).user?.uid ?: error("Firebase Auth returned null user")
    }

    suspend fun signInWithEmail(email: String, password: String): String = withContext(dispatchers.io) {
        auth.signInWithEmailAndPassword(email, password).user?.uid
            ?: error("Firebase Auth returned null user")
    }

    suspend fun createUserWithEmail(email: String, password: String): String = withContext(dispatchers.io) {
        auth.createUserWithEmailAndPassword(email, password).user?.uid
            ?: error("Firebase Auth returned null user")
    }

    // No own withContext: AccountDocStore.read/upsert each own their IO boundary, so this composes
    // two already-IO-confined calls. (Was double-wrapping before the store gained its own boundary.)
    suspend fun ensureAccountDoc(uid: String): AccountDto = healer.ensureAccountDoc(uid)

    suspend fun signOut() = withContext(dispatchers.io) {
        FrLog.d(FrLog.Tags.SignOut) { "data: auth.signOut() about to call" }
        auth.signOut()
        FrLog.d(FrLog.Tags.SignOut) { "data: auth.signOut() returned" }
    }

    fun sessions(): Flow<Session?> =
        auth.authStateChanged
            .onEach { user ->
                FrLog.d(FrLog.Tags.Session) { "data: authStateChanged user=${user?.uid ?: "null"}" }
            }
            .map { user ->
                val uid = user?.uid ?: return@map null
                try {
                    ensureAccountDoc(uid).toAccount()?.toSession()
                } catch (t: Throwable) {
                    // The account-doc round-trip can throw on cold start when Firebase has restored a
                    // PERSISTED user but the session is no longer valid server-side. If we let the throw
                    // escape, the `current` SharedFlow never emits its authoritative first value and the
                    // root nav sits on Splash FOREVER (the original cold-start hang). Classify instead:
                    if (t.indicatesRevokedSession()) {
                        // Account deleted/disabled or token revoked → the persisted user is stale. Sign
                        // it out so the next authStateChanged emits null and the root nav routes to
                        // SignIn, and wipe the account-scoped local data this bypassed-funnel sign-out
                        // would otherwise leave behind (see RevokedSessionCleanup). (The sibling
                        // accounts/{uid} snapshot path is guarded the same way; this authoritative
                        // session path was the one that wasn't.)
                        FrLog.w(FrLog.Tags.Session, t) { "data: session revoked server-side → signOut + wipe + null" }
                        revokedCleanup.endRevokedSession()
                        null
                    } else {
                        // Transient (network/unavailable/timeout): Firebase restored the user from local
                        // cache, so they ARE signed in — do NOT sign them out. Proceed on a minimal
                        // session (offline-first); the account-doc display data recovers via the separate
                        // AccountReadPort snapshot. Swallowing the throw is what keeps the flow alive.
                        FrLog.w(FrLog.Tags.Session, t) { "data: account-doc transient fail → minimal session" }
                        AccountId.of(uid).getOrNull()?.let { Session(accountId = it, activeCrewId = null) }
                    }
                }
            }
            .onEach { session ->
                FrLog.d(FrLog.Tags.Session) {
                    "data: sessions emit account=${session?.accountId?.value ?: "null"}"
                }
            }
    // No .flowOn(io): the only IO in the map body is ensureAccountDoc → AccountDocStore, which owns
    // its own withContext(io). Keeping flowOn here would double-wrap the same boundary (N1).

    /**
     * Proactively re-checks that the currently-signed-in user is still valid server-side, and signs
     * out if it isn't.
     *
     * Firebase auto-refreshes the ID token only ~hourly, so a server-side disable/delete or token
     * revocation can go undetected for up to an hour while the app stays open — the user keeps acting
     * on authenticated screens, with writes silently failing. [getIdToken(forceRefresh = true)] forces
     * an immediate server round-trip: on a revoked-session error we sign out (which nulls
     * `SessionProvider.current` → routes to SignIn); a transient failure is ignored so a network blip
     * never logs a valid user out. No-op when signed out. Called on app foreground (see FoodRatsApp).
     */
    suspend fun revalidateSession(): Unit = withContext(dispatchers.io) {
        val user = auth.currentUser ?: return@withContext
        try {
            user.getIdToken(true)
            FrLog.d(FrLog.Tags.Session) { "data: revalidate ok (${user.uid})" }
        } catch (t: Throwable) {
            if (t.indicatesRevokedSession()) {
                FrLog.w(FrLog.Tags.Session, t) { "data: revalidate → session revoked → signOut + wipe" }
                revokedCleanup.endRevokedSession()
            } else {
                FrLog.d(FrLog.Tags.Session) { "data: revalidate transient, keeping session: ${t.message}" }
            }
        }
    }
}
