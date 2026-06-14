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
) {
    companion object
}
