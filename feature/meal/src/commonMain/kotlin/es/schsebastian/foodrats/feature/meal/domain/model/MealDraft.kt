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
    /**
     * Ordered photos for this draft, up to
     * [es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy.MAX_PHOTOS_PER_MEAL]. Empty
     * means no photo yet. Replaces the old single nullable `plate` field — see [plate] for a
     * read-only single-photo convenience derivation ([plates]\[0\]).
     */
    val plates: List<Plate> = emptyList(),
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
) {
    /**
     * The primary (first) photo — a read-only convenience derivation ([plates]\[0\]) for call
     * sites that only care about a single cover photo (e.g. classification, the composer's hero
     * preview). `null` when [plates] is empty. Not a constructor parameter, so it plays no part
     * in `equals`/`hashCode`/`copy` — mutate [plates] instead.
     */
    val plate: Plate? get() = plates.firstOrNull()
}
