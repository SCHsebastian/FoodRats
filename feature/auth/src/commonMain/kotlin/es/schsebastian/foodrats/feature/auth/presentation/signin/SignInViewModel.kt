package es.schsebastian.foodrats.feature.auth.presentation.signin

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository

class SignInViewModel(private val auth: AuthRepository) :
    MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {

    override suspend fun handle(intent: SignInIntent) = when (intent) {
        SignInIntent.ContinueWithGoogle -> doSignIn()
        SignInIntent.DismissError -> update { it.copy(error = null) }
    }

    private suspend fun doSignIn() {
        update { it.copy(isLoading = true, error = null) }
        when (val r = auth.signInWithGoogle()) {
            is Result.Ok  -> {
                update { it.copy(isLoading = false, error = null) }
                emit(SignInEffect.SignedIn)
            }
            is Result.Err -> update { it.copy(isLoading = false, error = r.error) }
        }
    }
}
