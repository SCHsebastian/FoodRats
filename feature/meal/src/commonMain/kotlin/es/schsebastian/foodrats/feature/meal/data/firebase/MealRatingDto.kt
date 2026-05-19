package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class MealRatingDto(
    val score: Int? = null,
    val ratedAtEpochMs: Long? = null,
    val raterName: String? = null,
    val raterAvatarUrl: String? = null,
)
