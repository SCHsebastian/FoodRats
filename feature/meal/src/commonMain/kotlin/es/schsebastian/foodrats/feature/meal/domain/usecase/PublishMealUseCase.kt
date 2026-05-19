package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.datetime.TimeZone

class PublishMealUseCase(
    private val repository: MealRepository,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend operator fun invoke(draft: MealDraft): Result<Meal, MealError> {
        val today = MealDay.today(clock, zone)
        if (draft.day != today) return Result.failure(MealError.Publish.NotToday)
        val slot = draft.slot ?: return Result.failure(MealError.Publish.NoSlotSelected)
        if (draft.plate == null) return Result.failure(MealError.Validation.NoPhoto)
        if (draft.dish == null)  return Result.failure(MealError.Validation.Blank)

        val takenResult = repository.hasMealForSlot(draft.crewId, today, slot)
        if (takenResult is Result.Err) return Result.failure(takenResult.error)
        val taken = (takenResult as Result.Ok).value
        if (taken) return Result.failure(MealError.Publish.AlreadyPostedToday)

        return repository.publish(draft).also {
            if (it is Result.Ok) repository.clearDraft()
        }
    }
}
