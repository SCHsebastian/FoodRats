package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class RatingEntryDto(
    val score: Int = 0,
    val atMs: Long = 0L,
)
