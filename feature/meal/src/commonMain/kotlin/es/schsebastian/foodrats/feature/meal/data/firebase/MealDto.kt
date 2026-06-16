package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class MealDto(
    val id: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    val crewId: String? = null,
    val dayKey: String? = null,
    val slot: String = "lunch",
    // Storage object PATH of the plate photo (`crews/{crewId}/meals/{mealId}.jpg`), NOT a
    // URL — resolved to a signed URL at read time. (Author avatar is no longer denormalized
    // here: identity, including the avatar path, resolves live via AccountReadPort.)
    val platePath: String? = null,
    // Written by the server image pipeline a few seconds after publish (roadmap §5.1):
    // `thumbHash` is the base64-encoded ThumbHash bytes (the instant blur placeholder) and
    // `thumbnailPath` is the Storage object PATH of the downscaled JPEG
    // (`crews/{crewId}/meals/{mealId}_thumb.jpg`), resolved to a signed URL exactly like
    // [platePath]. Both null on docs not yet processed (or pre-pipeline) — tolerated.
    val thumbHash: String? = null,
    val thumbnailPath: String? = null,
    val dishName: String? = null,
    val description: String = "",
    // Optional GPS coordinates the user attached at compose time. Both null means
    // "no location attached"; the feed/detail UI renders a small map preview when set.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val publishedAtEpochMs: Long? = null,
    // Denormalized per-rater scores keyed by accountId (uid). Empty when no one has voted.
    val ratings: Map<String, RatingEntryDto> = emptyMap(),
    // Cached sum of all `ratings.values.score` values; written together with `ratings`.
    val ratingSum: Int = 0,
    // Cached count `ratings.size`; written together with `ratings`.
    val voterCount: Int = 0,
    // ONLY the user-confirmed ingredient slugs are persisted, plus the model version
    // that produced the seed detection. The raw classifier detection itself is a
    // compose-time picker seed (lives only on the in-memory MealDraft) and is never
    // written to the published Meal — so a meal records only food the user attested.
    val ingredients: List<String> = emptyList(),
    val classifierVersion: String? = null,
    // Cuisine slug stamped at publish from the detected dish via CuisineReadPort.loadDishCuisine
    // (roadmap §2.2: stamp-at-publish, stable across future dishCuisineMap changes). Null when the
    // dish wasn't classified or isn't in the cuisine map; a failed lookup never blocks publish.
    val cuisine: String? = null,
    // MealKind discriminator (spec 2026-06-14-meal-post-types §4.1/§6.1). Today the only live
    // value is "solo" — every meal has one author. The default reads pre-seam docs (and any
    // doc missing the field) as Solo; the mapper tolerates unknown values, also collapsing them
    // to Solo until the deferred Together build adds the explicit "together" arm + its fields.
    // Future (DEFERRED, §5): val coAuthorIds: List<String> = emptyList()
    val kind: String = "solo",
) {
    companion object
}
