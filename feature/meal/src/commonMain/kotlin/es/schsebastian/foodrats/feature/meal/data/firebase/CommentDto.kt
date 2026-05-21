package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    val id: String? = null,
    val authorId: String? = null,
    val text: String? = null,
    val createdAtEpochMs: Long? = null,
)
