package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.auth.data.firebase.AuthErrorMapper
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.toAccount
import es.schsebastian.foodrats.feature.auth.data.firebase.toSession
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

internal class FirebaseAuthRepository(
    private val googleClient: GoogleAuthClient,
    private val firebase: FirebaseAuthDataSource,
    private val errorMapper: AuthErrorMapper,
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
    private val repoScope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
            dispatchers.default +
            CoroutineExceptionHandler { _, t ->
                FrLog.w("AuthRepo", t) { "repoScope uncaught: ${t.message}" }
            },
    ),
) : AuthRepository {

    // The base session from Firebase Auth carries the account id; the active crew
    // id lives in DataStore (set by the Crew picker when the user picks/creates one).
    // Combine both so consumers see a complete Session and CaptureMealViewModel
    // doesn't error with CrewNotFound for signed-in users without an active crew.
    //
    // Shared as a hot flow so all consumers share one Firebase Auth listener. Uses
    // `shareIn(replay = 1)` rather than `stateIn(initialValue = null)` on purpose: a
    // synthetic `null` initial value is a LIE during auth restoration — Firebase hasn't
    // reported the persisted user yet (and `sessions()` only emits after a Firestore
    // `ensureAccountDoc` round-trip). That premature null made RootNavViewModel read
    // "signed out" and flash the SignIn screen before bouncing the user to the feed, and
    // made `requireCurrent()` return NotSignedIn mid-restore. With `shareIn` the FIRST
    // emitted value is authoritative; consumers that observe no value yet should treat it
    // as "auth resolving" (the root nav keeps the Splash screen until then).
    override val current: SharedFlow<Session?> =
        combine(firebase.sessions(), prefs.observe(Keys.ActiveCrewId)) { session, crewIdValue ->
            if (session == null) null
            else {
                val crewId = crewIdValue?.let { value ->
                    (CrewId.of(value) as? Result.Ok)?.value
                }
                session.copy(activeCrewId = crewId)
            }
        }.onEach { sess ->
            FrLog.d(FrLog.Tags.Session) {
                "repo.current emit account=${sess?.accountId?.value ?: "null"} " +
                    "crew=${sess?.activeCrewId?.value ?: "null"}"
            }
        }.shareIn(
            scope = repoScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1,
        )

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        current.first()?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        val token = when (val r = googleClient.signIn()) {
            is Result.Ok  -> r.value
            is Result.Err -> return Result.failure(r.error)
        }
        return try {
            val uid = firebase.signInWithGoogle(token)
            finishSignIn(uid)
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Session, AuthError> {
        return try {
            val uid = firebase.signInWithEmail(email.trim(), password)
            finishSignIn(uid)
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Session, AuthError> {
        return try {
            val uid = firebase.createUserWithEmail(email.trim(), password)
            finishSignIn(uid)
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    private suspend fun finishSignIn(uid: String): Result<Session, AuthError> {
        val account = firebase.ensureAccountDoc(uid).toAccount()
            ?: return Result.failure(AuthError.Firebase.Unavailable)
        // One-shot migration (2026-05-20): the prior build seeded every sign-in with
        // activeCrewId = "test-crew-1" via the now-removed dev-crew hardcode. Users
        // upgrading from that build still carry the legacy pref and get routed
        // straight to Main → publish hits PERMISSION_DENIED because they're not in
        // that crew's memberIds. Wipe the legacy value so the RootNavViewModel
        // emits NeedsCrew → CrewPicker and the user can pick or create a real crew.
        if (prefs.observe(Keys.ActiveCrewId).first() == LEGACY_DEV_CREW_ID) {
            prefs.clear(Keys.ActiveCrewId)
        }
        return Result.success(account.toSession())
    }

    private companion object {
        const val LEGACY_DEV_CREW_ID = "test-crew-1"
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        FrLog.d(FrLog.Tags.SignOut) { "repo: signOut entry" }
        return try {
            FrLog.d(FrLog.Tags.SignOut) { "repo: → firebase.signOut()" }
            firebase.signOut()
            FrLog.d(FrLog.Tags.SignOut) { "repo: → googleClient.signOut()" }
            googleClient.signOut()
            // Clear both the session token AND the active crew so the next sign-in
            // lands on CrewPicker instead of silently inheriting the previous user's
            // crew (the active-crew flow re-derives from prefs at session-restore time).
            // Also reset the post-signin notification-permission prompt flag so a
            // different account on this device sees the gate again.
            FrLog.d(FrLog.Tags.SignOut) { "repo: → prefs.clear(SessionToken, ActiveCrewId, NotificationsPermissionPrompted)" }
            prefs.clear(Keys.SessionToken)
            prefs.clear(Keys.ActiveCrewId)
            prefs.clear(Keys.NotificationsPermissionPrompted)
            FrLog.d(FrLog.Tags.SignOut) { "repo: signOut complete (Ok)" }
            Result.success(Unit)
        } catch (t: Throwable) {
            FrLog.w(FrLog.Tags.SignOut, t) { "repo: signOut threw: ${t.message}" }
            Result.failure(errorMapper.mapFirebase(t))
        }
    }
}
