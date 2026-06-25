package es.schsebastian.foodrats.feature.auth.presentation.signin

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.AuthMethod
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.preferences.NoopEulaAcceptance
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.launch

class SignInViewModel(
    private val auth: AuthRepository,
    private val tokenRegistration: TokenRegistrationPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
    // Acceptance is recorded IMPLICITLY on a successful sign-in (the agreement line on SignInScreen
    // states that continuing accepts the Terms + Community Guidelines — UGC compliance §6). Defaults
    // to NoopEulaAcceptance so existing fixtures that don't pass it stay green.
    private val eula: EulaPort = NoopEulaAcceptance,
) : MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {

    override suspend fun handle(intent: SignInIntent) = when (intent) {
        SignInIntent.ContinueWithGoogle -> doGoogle()
        SignInIntent.ContinueWithApple  -> doApple()
        SignInIntent.DismissError       -> update { it.copy(error = null, emailError = null, passwordError = null, appleComingSoon = false) }
        SignInIntent.SubmitEmail        -> doEmailSubmit()
        SignInIntent.ToggleMode         -> update {
            val next = if (it.mode == SignInMode.SignIn) SignInMode.SignUp else SignInMode.SignIn
            // Confirm-password only exists in SignUp — clear it (value + error + reveal) on every flip
            // so a stale match can't leak across modes.
            it.copy(
                mode = next,
                error = null, emailError = null, passwordError = null,
                confirmPassword = "", confirmPasswordError = null, showConfirmPassword = false,
                appleComingSoon = false,
            )
        }
        SignInIntent.TogglePasswordVisibility        -> update { it.copy(showPassword = !it.showPassword) }
        SignInIntent.ToggleConfirmPasswordVisibility -> update { it.copy(showConfirmPassword = !it.showConfirmPassword) }
        is SignInIntent.UpdateEmail     -> update { it.copy(email = intent.value, emailError = null, error = null, appleComingSoon = false) }
        is SignInIntent.UpdatePassword  -> update { it.copy(password = intent.value, passwordError = null, confirmPasswordError = null, error = null, appleComingSoon = false) }
        is SignInIntent.UpdateConfirmPassword -> update { it.copy(confirmPassword = intent.value, confirmPasswordError = null, error = null, appleComingSoon = false) }
    }

    private suspend fun doGoogle() {
        update { it.copy(isLoading = true, error = null, appleComingSoon = false) }
        emitFromResult(auth.signInWithGoogle(), AuthMethod.GOOGLE, isSignUp = false)
    }

    private suspend fun doApple() {
        update { it.copy(isLoading = true, error = null, appleComingSoon = false) }
        val r = auth.signInWithApple()
        // The flow is wired but "being built": the platform client returns NotYetAvailable, which
        // we surface as a friendly notice (not the red error banner) and don't log as a failure.
        // Real Apple errors (future) fall through to the normal error path.
        if (r is Result.Err && r.error == AuthError.AppleSignIn.NotYetAvailable) {
            update { it.copy(isLoading = false, appleComingSoon = true) }
            return
        }
        emitFromResult(r, AuthMethod.APPLE, isSignUp = false)
    }

    private suspend fun doEmailSubmit() {
        val s = currentState
        // Client-side validation first — cheaper than a network round-trip for obvious mistakes.
        val emailErr = if (!isEmailValid(s.email)) AuthError.EmailPassword.InvalidEmail else null
        val passwordErr = if (s.password.length < MIN_PASSWORD_LENGTH) AuthError.EmailPassword.WeakPassword else null
        // Confirm-password only gates SignUp; SignIn has no confirm field.
        val confirmErr = if (s.mode == SignInMode.SignUp && s.confirmPassword != s.password)
            AuthError.EmailPassword.PasswordMismatch else null
        if (emailErr != null || passwordErr != null || confirmErr != null) {
            update { it.copy(emailError = emailErr, passwordError = passwordErr, confirmPasswordError = confirmErr, error = null) }
            return
        }
        update { it.copy(isLoading = true, error = null, emailError = null, passwordError = null, confirmPasswordError = null) }
        val isSignUp = s.mode == SignInMode.SignUp
        val r = when (s.mode) {
            SignInMode.SignIn -> auth.signInWithEmail(s.email, s.password)
            SignInMode.SignUp -> auth.signUpWithEmail(s.email, s.password)
        }
        emitFromResult(r, AuthMethod.EMAIL, isSignUp)
    }

    private suspend fun emitFromResult(r: Result<*, AuthError>, method: AuthMethod, isSignUp: Boolean) {
        when (r) {
            is Result.Ok  -> {
                update { it.copy(isLoading = false, error = null) }
                analytics.track(if (isSignUp) AnalyticsEvent.SignedUp(method) else AnalyticsEvent.LoggedIn(method))
                // Implicit EULA / Community-Guidelines acceptance (UGC compliance §6): continuing
                // through sign-in records acceptance at the current version. Fire-and-forget — a
                // local-store write failure must never block the user from entering the app.
                viewModelScope.launch { eula.accept(CURRENT_EULA_VERSION) }
                viewModelScope.launch { tokenRegistration.registerCurrentDeviceToken() }
                emit(SignInEffect.SignedIn)
            }
            is Result.Err -> {
                val err = r.error
                analytics.track(AnalyticsEvent.SignInFailed(method, err::class.simpleName ?: "Unknown"))
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
