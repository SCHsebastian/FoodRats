package es.schsebastian.foodrats.feature.meal.domain.model

import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
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
    /**
     * The dish slug detected by the classifier (the `dishCuisineMap` key). Held on the draft
     * so the cuisine can be STAMPED at publish (roadmap §2.2). Set from
     * `DraftClassification.dishSlug` when a plate is classified; `null` when no classification
     * ran. The publish path resolves this to a [CuisineSlug] via
     * `CuisineReadPort.loadDishCuisine(...)` and writes `Meal.cuisine`.
     *
     * NOTE: not yet wired by `ComposePlateViewModel`/`UpdateMealDraftCommand`/`FirebaseMealRepository`
     * — see `docs/session/handoffs/w2-cuisine-passport-domain.md` for the exact write contract
     * the presentation/data task must implement.
     */
    val detectedDishSlug: String? = null,
)
