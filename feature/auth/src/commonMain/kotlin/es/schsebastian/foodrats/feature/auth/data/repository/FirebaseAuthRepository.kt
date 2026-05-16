package es.schsebastian.foodrats.feature.auth.data.repository

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.model.CrewId
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
            // TODO(scope = "feature:crew"): remove this dev-crew hardcode once the
            // Crew feature lets users create/pick a crew. Until then, smoke-testing
            // meal publishing needs a non-null Session.activeCrewId.
            val devCrewId = (CrewId.of(DEV_CREW_ID) as Result.Ok).value
            prefs.set(Keys.ActiveCrewId, DEV_CREW_ID)
            Result.success(account.toSession().copy(activeCrewId = devCrewId))
        } catch (t: Throwable) {
            Result.failure(errorMapper.mapFirebase(t))
        }
    }

    private companion object {
        const val DEV_CREW_ID = "test-crew-1"
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
