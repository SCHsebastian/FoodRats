package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AccentPalettePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

class SetAccentPaletteUseCase(private val port: AccentPalettePort) {
    suspend operator fun invoke(palette: AccentPalette): Result<Unit, ProfileError> =
        when (val r = port.set(palette)) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(r.error.toProfileError())
        }
}
