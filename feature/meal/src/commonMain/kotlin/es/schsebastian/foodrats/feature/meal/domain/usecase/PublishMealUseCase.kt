package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
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
        if (draft.plates.isEmpty()) return Result.failure(MealError.Validation.NoPhoto)
        if (draft.plates.size > MealPublishPolicy.MAX_PHOTOS_PER_MEAL) {
            return Result.failure(MealError.Validation.TooManyPhotos)
        }
        if (draft.dish == null)  return Result.failure(MealError.Validation.Blank)
        if (draft.audienceCrewIds.isEmpty()) return Result.failure(MealError.Publish.NoCrewSelected)
        // Slot is optional now — no NoSlotSelected check.

        // Audience-aware daily cap: a crew is "available" only while it holds fewer than
        // MAX_MEALS_PER_CREW_PER_DAY meals from this author today. Publishing proceeds while ANY
        // selected crew still has room; the repository fans the plate out to just those (skipping
        // full crews, and idempotently skipping crews this draft already reached on a retry).
        // When every selected crew is full, this is the daily limit (AlreadyPostedToday).
        val counts = when (val r = repository.mealCountsPerCrew(draft.audienceCrewIds, today)) {
            is Result.Ok  -> r.value
            is Result.Err -> return Result.failure(r.error)
        }
        val anyRoom = draft.audienceCrewIds.any {
            (counts[it] ?: 0) < MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY
        }
        if (!anyRoom) return Result.failure(MealError.Publish.AlreadyPostedToday)

        return repository.publish(draft).also {
            if (it is Result.Ok) repository.clearDraft()
        }
    }
}
