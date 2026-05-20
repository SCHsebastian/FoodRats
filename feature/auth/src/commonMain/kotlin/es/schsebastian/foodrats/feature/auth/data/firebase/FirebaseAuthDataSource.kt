package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.auth.data.google.GoogleIdToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class FirebaseAuthDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun signInWithGoogle(token: GoogleIdToken): String = withContext(dispatchers.io) {
        val cred = GoogleAuthProvider.credential(idToken = token.raw, accessToken = token.accessToken)
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

    suspend fun ensureAccountDoc(uid: String): AccountDto = withContext(dispatchers.io) {
        val ref = firestore.collection("accounts").document(uid)
        val snap = ref.get()
        if (snap.exists) snap.data<AccountDto>()
        else AccountDto(id = uid, handle = uid.take(8), displayName = "Rat", createdAtEpochMs = 0L)
            .also { ref.set(it) }
    }

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
                user?.uid?.let { uid ->
                    val acc = ensureAccountDoc(uid)
                    acc.toAccount()?.toSession()
                }
            }
            .onEach { session ->
                FrLog.d(FrLog.Tags.Session) {
                    "data: sessions emit account=${session?.accountId?.value ?: "null"}"
                }
            }
            .flowOn(dispatchers.io)
}
