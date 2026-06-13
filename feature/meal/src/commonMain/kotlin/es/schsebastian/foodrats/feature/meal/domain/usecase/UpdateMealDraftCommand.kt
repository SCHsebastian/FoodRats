package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

sealed interface UpdateMealDraftCommand {
    data class SetPhoto(val plate: Plate) : UpdateMealDraftCommand
    data class SetDish(val dish: DishName) : UpdateMealDraftCommand
    data class SetDescription(val description: Description) : UpdateMealDraftCommand
    data class SetSlot(val slot: MealSlot) : UpdateMealDraftCommand
    /** Pass `null` to clear an attached coordinate pair. */
    data class SetCoordinates(val coordinates: Coordinates?) : UpdateMealDraftCommand

    /**
     * Records a fresh classifier run: overwrites the detected set and stamps the
     * classifier version. Issued once per captured photo. Does NOT touch the
     * user-confirmed `ingredients` list — the detected set is only the ingredient
     * picker's initial selection; confirmation is the user's explicit act,
     * recorded by [SetIngredients].
     */
    data class SetDetected(
        val detected: List<IngredientSlug>,
        val version: String,
    ) : UpdateMealDraftCommand

    /** Records the user's edited selection; leaves the detected set untouched. */
    data class SetIngredients(val ingredients: List<IngredientSlug>) : UpdateMealDraftCommand
}
