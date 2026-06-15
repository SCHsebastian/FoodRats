package es.schsebastian.foodrats.core.domain.meal

import kotlin.time.Instant

/**
 * One cell of the ingredient bingo (the "Pokédex"): a catalog ingredient plus whether the user has
 * collected it (posted a meal that CONFIRMS it) and, if so, WHEN they first did (the earliest
 * publish instant of a meal carrying this ingredient — the moment it was "caught"). Locked cells
 * have `firstCollectedAt == null`. Mirrors
 * [CollectedCuisine][es.schsebastian.foodrats.core.domain.cuisine.CollectedCuisine].
 */
data class CollectedIngredient(
    val ingredient: Ingredient,
    val collected: Boolean,
    val firstCollectedAt: Instant?,
)

/**
 * The derived bingo read model: every catalog ingredient as a [CollectedIngredient] plus the
 * progress counts ("142 / 226"). `cells` preserves the catalog's iteration order so the grid is
 * stable. Mirrors [CuisinePassport][es.schsebastian.foodrats.core.domain.cuisine.CuisinePassport].
 */
data class IngredientBingo(
    val cells: List<CollectedIngredient>,
) {
    /** Distinct collected ingredients (the "collected" of "collected / total"). */
    val collectedCount: Int get() = cells.count { it.collected }

    /** Total catalog ingredients (the "total" of "collected / total"). */
    val totalCount: Int get() = cells.size
}

/**
 * The PURE ingredient-bingo derivation: same inputs → same bingo. No I/O, no Clock, no Flow. The
 * caller resolves [catalog] from [IngredientReadPort.observeCatalog] and passes the user's meals.
 *
 * An ingredient is *collected* when at least one of [meals] CONFIRMS that slug via
 * [Meal.ingredients] — the user-confirmed list ONLY. AI [Meal.detectedIngredients] are deliberately
 * EXCLUDED (roadmap §2.3 + the known merge bug): an unconfirmed AI guess must never light a cell.
 * `firstCollectedAt` is the earliest [Meal.publishedAt] among that ingredient's confirming meals.
 * Ingredients NOT in [catalog] (unknown / future slugs) contribute nothing — they neither collect
 * nor appear as cells.
 *
 * Performance: the collected set is built in a single pass over [meals] (O(meals × confirmed-per-
 * meal)), then the cells are mapped over [catalog] with O(1) map lookups — NOT O(meals × catalog).
 * Fine for the ~226-ingredient catalog.
 *
 * @param catalog the closed ingredient catalog keyed by slug (defines the grid's cells + order).
 * @param meals the user's published meals to score against (only `ingredients` is read).
 */
fun deriveIngredientBingo(
    catalog: Map<IngredientSlug, Ingredient>,
    meals: List<Meal>,
): IngredientBingo {
    // Earliest publish instant per CONFIRMED ingredient slug. detectedIngredients are ignored;
    // slugs absent from the catalog drop out at the mapping step below.
    val firstCollectedBySlug: Map<IngredientSlug, Instant> = buildMap {
        for (meal in meals) {
            for (slug in meal.ingredients) {
                val existing = this[slug]
                if (existing == null || meal.publishedAt < existing) put(slug, meal.publishedAt)
            }
        }
    }

    val cells = catalog.values.map { ingredient ->
        val firstCollectedAt = firstCollectedBySlug[ingredient.slug]
        CollectedIngredient(
            ingredient = ingredient,
            collected = firstCollectedAt != null,
            firstCollectedAt = firstCollectedAt,
        )
    }
    return IngredientBingo(cells)
}
