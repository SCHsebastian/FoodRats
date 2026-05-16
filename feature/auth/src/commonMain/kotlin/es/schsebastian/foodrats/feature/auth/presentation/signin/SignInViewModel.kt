package es.schsebastian.foodrats.feature.auth.presentation.signin

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.auth.domain.repository.AuthRepository

class SignInViewModel(private val auth: AuthRepository) :
    MviViewModel<SignInState, SignInIntent, SignInEffect>(SignInState()) {

    override suspend fun handle(intent: SignInIntent) = when (intent) {
        SignInIntent.ContinueWithGoogle -> {
            update { it.copy(isLoading = true, error = null) }
            val r = auth.signInWithGoogle()
            update { it.copy(isLoading = false, error = if (r is Result.Err) r.error else null) }
            if (r is Result.Ok) emit(SignInEffect.SignedIn)
        }
    }
}
