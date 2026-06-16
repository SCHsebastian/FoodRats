package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.first

/**
 * Deletes the author's own plate from every crew it was shared to — the "delete my post"
 * action. A plate published to multiple crews is one logical post fanned out to per-crew
 * copies (each with its own image), so removing it clears it everywhere; the per-crew image
 * blobs are reclaimed server-side by the `onMealDeleted` Cloud Function.
 *
 * Distinct from [DeleteMealUseCase], which removes a single crew's copy — that's crew-owner
 * moderation (an owner may clear a meal from *their* crew, not the author's other crews).
 */
class DeleteMyMealUseCase(
    private val meals: MealDeletePort,
    private val crews: CrewMembershipPort,
) {
    suspend operator fun invoke(
        authorId: AccountId,
        day: MealDay,
        slot: MealSlot,
    ): Result<Unit, MealDeleteError> {
        val crewIds = crews.observeMyCrews(authorId).first().map { it.id }.toSet()
        return meals.deleteFromAllCrews(crewIds, authorId, day, slot)
    }
}
