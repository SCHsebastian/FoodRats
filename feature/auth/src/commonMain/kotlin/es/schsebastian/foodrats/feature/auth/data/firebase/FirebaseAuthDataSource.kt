package es.schsebastian.foodrats.feature.auth.data.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.feature.auth.data.google.GoogleIdToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FirebaseAuthDataSource(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun signInWithGoogle(token: GoogleIdToken): String = withContext(dispatchers.io) {
        val cred = GoogleAuthProvider.credential(idToken = token.raw, accessToken = null)
        auth.signInWithCredential(cred).user?.uid ?: error("Firebase Auth returned null user")
    }

    suspend fun ensureAccountDoc(uid: String): AccountDto = withContext(dispatchers.io) {
        val ref = firestore.collection("accounts").document(uid)
        val snap = ref.get()
        if (snap.exists) snap.data<AccountDto>()
        else AccountDto(id = uid, handle = uid.take(8), displayName = "Rat", createdAtEpochMs = 0L)
            .also { ref.set(it) }
    }

    suspend fun signOut() = withContext(dispatchers.io) { auth.signOut() }

    fun sessions(): Flow<Session?> =
        auth.authStateChanged.map { user ->
            user?.uid?.let { uid ->
                val acc = ensureAccountDoc(uid)
                acc.toAccount()?.toSession()
            }
        }.flowOn(dispatchers.io)
}
