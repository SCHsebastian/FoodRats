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
    /** True when the author has edited this comment — drives the "(edited)" tag. */
    val isEdited: Boolean = false,
    val canDelete: Boolean = false,
    /** True when the viewer authored this comment and may edit it (author-only). */
    val canEdit: Boolean = false,
    /** Raw author id, used to report/block this comment's author (UGC compliance §4/§5). */
    val authorId: String = "",
    /** True when the viewer may report/block this commenter (not their own comment). */
    val canModerate: Boolean = false,
    /** True when the viewer authored this comment — drives right-vs-left chat-bubble alignment. */
    val isOwnComment: Boolean = false,
)
