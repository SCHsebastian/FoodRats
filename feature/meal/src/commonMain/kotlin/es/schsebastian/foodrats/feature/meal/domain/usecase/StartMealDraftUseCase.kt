package es.schsebastian.foodrats.feature.meal.domain.usecase

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
    suspend operator fun invoke(crewId: CrewId, authorId: AccountId): Result<MealDraft, MealError> {
        val fresh = MealDraft(
            crewId = crewId,
            authorId = authorId,
            day = MealDay.today(clock, zone),
            plate = null, dish = null, tags = emptyList(),
        )
        return repository.saveDraft(fresh).map { fresh }
    }
}
