package es.schsebastian.foodrats.feature.auth.presentation.signin

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

enum class SignInMode { SignIn, SignUp }

data class SignInState(
    val email: String = "",
    val password: String = "",
    val mode: SignInMode = SignInMode.SignIn,
    val isLoading: Boolean = false,
    val emailError: AuthError.EmailPassword? = null,
    val passwordError: AuthError.EmailPassword? = null,
    val error: AuthError? = null,
) : MviState

sealed interface SignInIntent : MviIntent {
    data object ContinueWithGoogle : SignInIntent
    data class UpdateEmail(val value: String) : SignInIntent
    data class UpdatePassword(val value: String) : SignInIntent
    data object ToggleMode : SignInIntent
    data object SubmitEmail : SignInIntent
    data object DismissError : SignInIntent
}

sealed interface SignInEffect : MviEffect {
    data object SignedIn : SignInEffect
}
