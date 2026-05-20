package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.domain.meal.MealCommentId

data class CommentRowUi(
    val id: MealCommentId,
    val displayName: String,
    val avatarUrl: String?,
    val text: String,
    val relative: RelativeTimestamp,
    val loading: Boolean,
    val isDeleted: Boolean,
)
