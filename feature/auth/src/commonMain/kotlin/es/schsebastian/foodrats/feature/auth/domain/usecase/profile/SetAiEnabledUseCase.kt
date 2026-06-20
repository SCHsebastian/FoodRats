package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.preferences.AiPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

class SetAiEnabledUseCase(private val port: AiPreferencePort) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit, ProfileError> =
        when (val r = port.set(enabled)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
}
