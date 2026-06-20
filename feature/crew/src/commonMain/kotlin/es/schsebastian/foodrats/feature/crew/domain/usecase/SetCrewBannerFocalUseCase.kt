package es.schsebastian.foodrats.feature.crew.domain.usecase

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.repository.CrewRepository

/**
 * Sets the crew banner's vertical focal point (C9) — the `0f..1f` position the fixed-height feed
 * crop anchors to (0 = top, 0.5 = center, 1 = bottom). Delegates to [CrewRepository.setBannerFocalY]
 * which enforces owner-only authorization. [focalY] is clamped to the valid range here so an
 * out-of-range gesture value can never reach the write.
 */
class SetCrewBannerFocalUseCase(
    private val repository: CrewRepository,
    private val session: SessionProvider,
) {
    suspend operator fun invoke(crewId: CrewId, focalY: Float): Result<Unit, CrewError> {
        val accountId = when (val s = session.requireCurrent()) {
            is Result.Ok -> s.value.accountId
            is Result.Err -> return Result.failure(CrewError.Backend.Unavailable)
        }
        return repository.setBannerFocalY(crewId, accountId, focalY.coerceIn(0f, 1f))
    }
}
