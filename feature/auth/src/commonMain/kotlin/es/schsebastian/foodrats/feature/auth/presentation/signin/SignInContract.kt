package es.schsebastian.foodrats.feature.auth.presentation.signin

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.auth.domain.error.AuthError

data class SignInState(val isLoading: Boolean = false, val error: AuthError? = null) : MviState
sealed interface SignInIntent : MviIntent {
    data object ContinueWithGoogle : SignInIntent
    data object DismissError : SignInIntent
}
sealed interface SignInEffect : MviEffect { data object SignedIn : SignInEffect }
