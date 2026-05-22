package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

class SetLocaleUseCase(private val port: LocalePort) {
    suspend operator fun invoke(locale: AppLocale): Result<Unit, ProfileError> =
        when (val r = port.set(locale)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
}
