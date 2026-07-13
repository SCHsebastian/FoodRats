package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.map
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.datetime.TimeZone

class StartMealDraftUseCase(
    private val repository: MealRepository,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    /**
     * Starts a fresh draft for [authorId], seeded with [audienceCrewIds] as the
     * publish audience (the composer defaults this to the active crew the user
     * launched from, falling back to a saved default / all crews, and the user may
     * change it before publishing).
     */
    suspend operator fun invoke(
        authorId: AccountId,
        audienceCrewIds: Set<CrewId>,
    ): Result<MealDraft, MealError> {
        val fresh = MealDraft(
            audienceCrewIds = audienceCrewIds,
            authorId = authorId,
            day = MealDay.today(clock, zone),
            dish = null, description = Description.EMPTY,
        )
        return repository.saveDraft(fresh).map { fresh }
    }
}
