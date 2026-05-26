package es.schsebastian.foodrats.core.domain.meal

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
    suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError>
}

sealed interface MealDeleteError {
    /** The caller is neither the meal's author nor the crew owner. */
    data object NotAuthorOrOwner : MealDeleteError
    data object NotFound : MealDeleteError
    data object Unavailable : MealDeleteError
}
