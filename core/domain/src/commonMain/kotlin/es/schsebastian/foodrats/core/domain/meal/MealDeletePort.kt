package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Cross-context port for deleting a meal. Lives in `:core:domain` so `:feature:feed`
 * (which owns the delete affordance in the UI) can call it without depending on
 * `:feature:meal`, mirroring how `MealReadPort` / `MealRatingPort` are consumed.
 * Authorization (author OR crew owner) is enforced by Firestore security rules; a
 * rejection surfaces as [MealDeleteError.NotAuthorOrOwner].
 */
interface MealDeletePort {
    /**
     * Removes a single crew's copy of a meal. Used for crew-owner moderation — an owner
     * may delete a meal from *their* crew only, leaving the author's copies in their
     * other crews untouched.
     */
    suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError>

    /**
     * Removes the author's own (day, slot) plate from every crew in [crewIds] — the
     * "delete my post" action. A plate published to several crews is one logical post
     * fanned out to per-crew copies (each with its own image), so deleting it should
     * clear it everywhere. Best-effort per crew: a crew with no copy is a no-op, and a
     * per-crew failure does not abort the rest. The per-crew image blobs are reclaimed
     * server-side by the `onMealDeleted` Cloud Function.
     */
    suspend fun deleteFromAllCrews(
        crewIds: Set<CrewId>,
        authorId: AccountId,
        day: MealDay,
        slot: MealSlot,
    ): Result<Unit, MealDeleteError>
}

sealed interface MealDeleteError {
    /** The caller is neither the meal's author nor the crew owner. */
    data object NotAuthorOrOwner : MealDeleteError
    data object NotFound : MealDeleteError
    data object Unavailable : MealDeleteError
}
