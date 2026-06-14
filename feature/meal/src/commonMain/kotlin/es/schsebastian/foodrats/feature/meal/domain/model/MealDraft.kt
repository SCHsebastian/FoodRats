package es.schsebastian.foodrats.feature.meal.domain.model

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

data class MealDraft(
    /**
     * The crews this plate will be published to (the chosen audience). A meal is one
     * logical post fanned out to a per-crew copy in each of these crews; the picker
     * defaults to all the author's crews and the user may narrow it. Never empty at
     * publish time — `PublishMealUseCase` rejects an empty audience with
     * `MealError.Publish.NoCrewSelected`.
     */
    val audienceCrewIds: Set<CrewId>,
    val authorId: AccountId,
    val day: MealDay,
    val plate: Plate?,
    val dish: DishName?,
    val description: Description,
    val slot: MealSlot? = null,
    val coordinates: Coordinates? = null,
    val ingredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierVersion: String? = null,
)
