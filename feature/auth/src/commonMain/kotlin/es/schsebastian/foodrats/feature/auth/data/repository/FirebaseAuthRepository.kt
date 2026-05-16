package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.feature.auth.data.firebase.AuthErrorMapper
import es.schsebastian.foodrats.feature.auth.data.firebase.FirebaseAuthDataSource
import es.schsebastian.foodrats.feature.auth.data.firebase.toAccount
import es.schsebastian.foodrats.feature.auth.data.firebase.toSession
import es.schsebastian.foodrats.feature.auth.data.google.GoogleAuthClient
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

internal class FirebaseAuthRepository(
    private val googleClient: GoogleAuthClient,
    private val firebase: FirebaseAuthDataSource,
    private val errorMapper: AuthErrorMapper,
    private val prefs: AppPreferences,
) : AuthRepository {

    override val current: Flow<Session?> = firebase.sessions()

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        current.first()?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        val token = when (val r = googleClient.signIn()) {
            is Result.Ok  -> r.value
            is Result.Err -> return Result.failure(r.error)
        }
        return try {
            val uid = firebase.signInWithGoogle(token)
            val account = firebase.ensureAccountDoc(uid).toAccount()
                ?: return Result.failure(AuthError.Firebase.Unavailable)
            Result.success(account.toSession())
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        return try {
            firebase.signOut()
            googleClient.signOut()
            prefs.clear(Keys.SessionToken)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }
}
