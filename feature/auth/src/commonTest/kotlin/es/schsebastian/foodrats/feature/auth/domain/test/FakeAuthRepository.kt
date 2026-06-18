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
    private val emailSignInResult: Result<Session, AuthError> = signInResult,
    private val emailSignUpResult: Result<Session, AuthError> = signInResult,
    private val appleSignInResult: Result<Session, AuthError> = Result.failure(AuthError.AppleSignIn.NotYetAvailable),
    private val initialSession: Session? = null,
) : AuthRepository {
    private val sessionFlow = MutableStateFlow(initialSession)
    override val current: Flow<Session?> = sessionFlow

    var lastEmail: String? = null
    var lastPassword: String? = null
    var lastMode: String? = null

    override suspend fun requireCurrent(): Result<Session, SessionError> =
        sessionFlow.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)

    override suspend fun signInWithGoogle(): Result<Session, AuthError> {
        if (signInResult is Result.Ok) sessionFlow.value = signInResult.value
        return signInResult
    }

    override suspend fun signInWithApple(): Result<Session, AuthError> {
        if (appleSignInResult is Result.Ok) sessionFlow.value = appleSignInResult.value
        return appleSignInResult
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Session, AuthError> {
        lastEmail = email; lastPassword = password; lastMode = "signIn"
        if (emailSignInResult is Result.Ok) sessionFlow.value = emailSignInResult.value
        return emailSignInResult
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Session, AuthError> {
        lastEmail = email; lastPassword = password; lastMode = "signUp"
        if (emailSignUpResult is Result.Ok) sessionFlow.value = emailSignUpResult.value
        return emailSignUpResult
    }

    override suspend fun signOut(): Result<Unit, AuthError> {
        sessionFlow.value = null
        return Result.success(Unit)
    }
}
