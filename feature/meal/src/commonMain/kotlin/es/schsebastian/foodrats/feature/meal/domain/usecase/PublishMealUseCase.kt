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
        if (draft.audienceCrewIds.isEmpty()) return Result.failure(MealError.Publish.NoCrewSelected)

        // A slot is "already posted today" only when it's taken in EVERY selected crew —
        // i.e. there is no crew left to receive this plate. While any selected crew is
        // still free, publishing proceeds and the repository fans the plate out to just
        // the free ones (the deterministic per-crew meal id keeps it idempotent on retry).
        var anyFree = false
        for (crewId in draft.audienceCrewIds) {
            when (val r = repository.hasMealForSlot(crewId, today, slot)) {
                is Result.Ok  -> if (!r.value) anyFree = true
                is Result.Err -> return Result.failure(r.error)
            }
        }
        if (!anyFree) return Result.failure(MealError.Publish.AlreadyPostedToday)

        return repository.publish(draft).also {
            if (it is Result.Ok) repository.clearDraft()
        }
    }
}
