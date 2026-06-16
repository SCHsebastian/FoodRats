package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.meal.Meal
import kotlin.time.Instant

/**
 * One cell of the cuisine passport: a catalog cuisine plus whether the user has collected it
 * and, if so, WHEN they first did (the earliest publish instant of a meal stamped with this
 * cuisine — the "stamp on the passport"). Locked cells have `firstCollectedAt == null`.
 */
data class CollectedCuisine(
    val cuisine: Cuisine,
    val collected: Boolean,
    val firstCollectedAt: Instant?,
)

/**
 * The derived passport read model: every catalog cuisine as a [CollectedCuisine] plus the
 * progress counts. `cells` preserves the catalog's iteration order so the grid is stable.
 */
data class CuisinePassport(
    val cells: List<CollectedCuisine>,
) {
    /** Distinct collected cuisines (the "collected" of "collected / total"). */
    val collectedCount: Int get() = cells.count { it.collected }

    /** Total catalog cuisines (the "total" of "collected / total"). */
    val totalCount: Int get() = cells.size
}

/**
 * The PURE cuisine-passport derivation: same inputs → same passport. No I/O, no Clock, no
 * Flow. The caller resolves [catalog] from [CuisineReadPort.observeCatalog] and passes the
 * user's CONFIRMED meals (the same confirmed-vs-AI-detected rule stats/achievements use — a
 * meal's cuisine is STAMPED at publish, so it is always "confirmed"; do NOT derive cuisine from
 * AI detections here).
 *
 * A cuisine is *collected* when at least one of [confirmedMeals] carries that cuisine slug
 * ([Meal.cuisine]). `firstCollectedAt` is the earliest [Meal.publishedAt] among that cuisine's
 * meals. Meals with a `null` cuisine (older meals published before stamping shipped, or dishes
 * absent from `dishCuisineMap`) and cuisines NOT in [catalog] (unknown future slugs) contribute
 * nothing — they neither collect nor appear as cells.
 *
 * @param catalog the closed cuisine catalog keyed by slug (defines the grid's cells + order).
 * @param confirmedMeals the user's published meals to score against (cuisine stamped at publish).
 */
fun deriveCuisinePassport(
    catalog: Map<CuisineSlug, Cuisine>,
    confirmedMeals: List<Meal>,
): CuisinePassport {
    // Earliest publish instant per stamped cuisine slug. Unknown / unstamped meals drop out.
    val firstCollectedBySlug: Map<CuisineSlug, Instant> = buildMap {
        for (meal in confirmedMeals) {
            val slug = meal.cuisine ?: continue
            if (slug !in catalog) continue
            val existing = this[slug]
            if (existing == null || meal.publishedAt < existing) put(slug, meal.publishedAt)
        }
    }

    val cells = catalog.values.map { cuisine ->
        val firstCollectedAt = firstCollectedBySlug[cuisine.slug]
        CollectedCuisine(
            cuisine = cuisine,
            collected = firstCollectedAt != null,
            firstCollectedAt = firstCollectedAt,
        )
    }
    return CuisinePassport(cells)
}
