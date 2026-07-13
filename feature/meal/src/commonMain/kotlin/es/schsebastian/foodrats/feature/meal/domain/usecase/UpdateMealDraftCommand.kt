package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

sealed interface UpdateMealDraftCommand {
    /**
     * Appends [plate] to the end of the draft's ordered [es.schsebastian.foodrats.feature.meal.domain.model.MealDraft.plates].
     * Cap-guarded: [UpdateMealDraftUseCase] rejects an append past
     * [es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy.MAX_PHOTOS_PER_MEAL] with
     * `MealError.Validation.TooManyPhotos` rather than silently dropping it — the caller decides
     * whether to surface that or ignore it.
     */
    data class AddPhoto(val plate: Plate) : UpdateMealDraftCommand
    /** Removes the photo at [index]. Out-of-bounds is a no-op (defensive; the UI should never offer it). */
    data class RemovePhotoAt(val index: Int) : UpdateMealDraftCommand
    /** Moves the photo at [fromIndex] to [toIndex]. Either index out of bounds is a no-op. */
    data class MovePhoto(val fromIndex: Int, val toIndex: Int) : UpdateMealDraftCommand
    data class SetDish(val dish: DishName) : UpdateMealDraftCommand
    data class SetDescription(val description: Description) : UpdateMealDraftCommand
    /** `null` clears the slot — it's an optional label, not required to publish. */
    data class SetSlot(val slot: MealSlot?) : UpdateMealDraftCommand
    /** Pass `null` to clear an attached coordinate pair. */
    data class SetCoordinates(val coordinates: Coordinates?) : UpdateMealDraftCommand

    /** Sets which crews the plate will be shared with (the publish audience). */
    data class SetAudience(val crewIds: Set<CrewId>) : UpdateMealDraftCommand

    /**
     * Records a fresh classifier run: overwrites the detected set, the detected dish
     * slug, and stamps the classifier version. Issued once per captured photo. Does NOT
     * touch the user-confirmed `ingredients` list — the detected set is only the ingredient
     * picker's initial selection; confirmation is the user's explicit act, recorded by
     * [SetIngredients]. The [dishSlug] is the `dishCuisineMap` key; the publish path resolves
     * it to a `Meal.cuisine` via `CuisineReadPort.loadDishCuisine` (roadmap §2.2 stamp-at-publish).
     */
    data class SetDetected(
        val detected: List<IngredientSlug>,
        val dishSlug: String,
        val version: String,
    ) : UpdateMealDraftCommand

    /** Records the user's edited selection; leaves the detected set untouched. */
    data class SetIngredients(val ingredients: List<IngredientSlug>) : UpdateMealDraftCommand
}
