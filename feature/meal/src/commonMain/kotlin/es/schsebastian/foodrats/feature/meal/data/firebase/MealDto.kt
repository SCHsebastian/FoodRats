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
    val photoUrl: String? = null,
    val score: Int? = null,
    val dishName: String? = null,
    val tags: List<String> = emptyList(),
    val publishedAtEpochMs: Long? = null,
)
