package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class MealDto(
    val id: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val crewId: String? = null,
    val dayKey: String? = null,
    val slot: String = "lunch",
    val photoUrl: String? = null,
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
)
