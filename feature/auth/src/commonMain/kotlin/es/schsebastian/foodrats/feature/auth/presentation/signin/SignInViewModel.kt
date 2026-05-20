package es.schsebastian.foodrats.feature.auth.presentation.signin

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import kotlinx.coroutines.launch

class SignInViewModel(
    private val auth: AuthRepository,
    private val registerDeviceToken: RegisterDeviceTokenUseCase,
) : MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {

    override suspend fun handle(intent: SignInIntent) = when (intent) {
        SignInIntent.ContinueWithGoogle -> doGoogle()
        SignInIntent.DismissError       -> update { it.copy(error = null, emailError = null, passwordError = null) }
        SignInIntent.SubmitEmail        -> doEmailSubmit()
        SignInIntent.ToggleMode         -> update {
            val next = if (it.mode == SignInMode.SignIn) SignInMode.SignUp else SignInMode.SignIn
            it.copy(mode = next, error = null, emailError = null, passwordError = null)
        }
        is SignInIntent.UpdateEmail     -> update { it.copy(email = intent.value, emailError = null, error = null) }
        is SignInIntent.UpdatePassword  -> update { it.copy(password = intent.value, passwordError = null, error = null) }
    }

    private suspend fun doGoogle() {
        update { it.copy(isLoading = true, error = null) }
        emitFromResult(auth.signInWithGoogle())
    }

    private suspend fun doEmailSubmit() {
        val s = currentState
        // Client-side validation first — cheaper than a network round-trip for obvious mistakes.
        val emailErr = if (!isEmailValid(s.email)) AuthError.EmailPassword.InvalidEmail else null
        val passwordErr = if (s.password.length < MIN_PASSWORD_LENGTH) AuthError.EmailPassword.WeakPassword else null
        if (emailErr != null || passwordErr != null) {
            update { it.copy(emailError = emailErr, passwordError = passwordErr, error = null) }
            return
        }
        update { it.copy(isLoading = true, error = null, emailError = null, passwordError = null) }
        val r = when (s.mode) {
            SignInMode.SignIn -> auth.signInWithEmail(s.email, s.password)
            SignInMode.SignUp -> auth.signUpWithEmail(s.email, s.password)
        }
        emitFromResult(r)
    }

    private suspend fun emitFromResult(r: Result<*, AuthError>) {
        when (r) {
            is Result.Ok  -> {
                update { it.copy(isLoading = false, error = null) }
                viewModelScope.launch { registerDeviceToken() }
                emit(SignInEffect.SignedIn)
            }
            is Result.Err -> {
                val err = r.error
                update {
                    when (err) {
                        AuthError.EmailPassword.InvalidEmail,
                        AuthError.EmailPassword.EmailAlreadyInUse ->
                            it.copy(isLoading = false, emailError = err as AuthError.EmailPassword, error = null)
                        AuthError.EmailPassword.WeakPassword,
                        AuthError.EmailPassword.WrongCredentials ->
                            it.copy(isLoading = false, passwordError = err as AuthError.EmailPassword, error = null)
                        else -> it.copy(isLoading = false, error = err)
                    }
                }
            }
        }
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        /** Minimal email shape check — Firebase does the real validation server-side. */
        fun isEmailValid(email: String): Boolean {
            val trimmed = email.trim()
            val at = trimmed.indexOf('@')
            return at > 0 && at < trimmed.length - 1 && trimmed.indexOf('.', at) > at + 1
        }
    }
}
