package es.schsebastian.foodrats.feature.ingredient.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore shape of a `cuisines/{slug}` doc. Mirrors [IngredientDto] but leaner — a cuisine is
 * just a slug + localized names + iconKey (no category/aliases). `ignoreUnknownKeys`-tolerant
 * reads drop the seeder's extra `updatedAt` field (intentionally NOT declared here).
 */
@Serializable
data class CuisineDto(
    val slug: String = "",
    val names: Map<String, String> = emptyMap(),
    val iconKey: String? = null,
)

/**
 * Firestore shape of a `dishCuisineMap/{dishSlug}` doc: a Food-101 dish → its single cuisine.
 * The seeder also writes `modelLabel`/`updatedAt`, intentionally NOT declared here (dropped on read).
 */
@Serializable
data class DishCuisineMapDto(
    val dishSlug: String = "",
    val cuisine: String = "",
)
