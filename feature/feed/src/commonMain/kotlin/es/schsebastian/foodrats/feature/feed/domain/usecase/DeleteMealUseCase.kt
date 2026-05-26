package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Deletes a meal. Authorization (author OR crew owner) is enforced server-side by the
 * Firestore rules; a rejection surfaces as [MealDeleteError.NotAuthorOrOwner]. The UI
 * separately gates the delete affordance with the same author/owner check so the action
 * is only offered when it will succeed.
 */
class DeleteMealUseCase(private val meals: MealDeletePort) {
    suspend operator fun invoke(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError> =
        meals.delete(crewId, mealId)
}
