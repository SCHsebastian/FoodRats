package es.schsebastian.foodrats.feature.auth.domain.test

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository(
    private val signInResult: Result<Session, AuthError>,
    private val initialSession: Session? = null,
) : AuthRepository {
    private val sessionFlow = MutableStateFlow(initialSession)
    override val current: Flow<Session?> = sessionFlow

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        sessionFlow.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        if (signInResult is Result.Ok) sessionFlow.value = signInResult.value
        return signInResult
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        sessionFlow.value = null
        return Result.success(Unit)
    }
}
