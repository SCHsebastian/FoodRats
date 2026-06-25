package es.schsebastian.foodrats.feature.auth.presentation.signin

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

enum class SignInMode { SignIn, SignUp }

data class SignInState(
    val email: String = "",
    val password: String = "",
    /** Sign-up only: the re-typed password the user must match before the account is created. */
    val confirmPassword: String = "",
    val mode: SignInMode = SignInMode.SignIn,
    val isLoading: Boolean = false,
    val emailError: AuthError.EmailPassword? = null,
    val passwordError: AuthError.EmailPassword? = null,
    /** Sign-up only: set when [confirmPassword] doesn't match [password]. */
    val confirmPasswordError: AuthError.EmailPassword? = null,
    /** When true the password field renders its value in clear text (eye open). */
    val showPassword: Boolean = false,
    /** When true the confirm-password field renders its value in clear text (eye open). */
    val showConfirmPassword: Boolean = false,
    val error: AuthError? = null,
    /** Set when the user taps "Continue with Apple" while the flow is still "being built". */
    val appleComingSoon: Boolean = false,
) : MviState

sealed interface SignInIntent : MviIntent {
    data object ContinueWithGoogle : SignInIntent
    data object ContinueWithApple : SignInIntent
    data class UpdateEmail(val value: String) : SignInIntent
    data class UpdatePassword(val value: String) : SignInIntent
    data class UpdateConfirmPassword(val value: String) : SignInIntent
    data object TogglePasswordVisibility : SignInIntent
    data object ToggleConfirmPasswordVisibility : SignInIntent
    data object ToggleMode : SignInIntent
    data object SubmitEmail : SignInIntent
    data object DismissError : SignInIntent
}

sealed interface SignInEffect : MviEffect {
    data object SignedIn : SignInEffect
}
