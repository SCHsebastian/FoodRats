package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    val id: String? = null,
    val authorId: String? = null,
    val text: String? = null,
    val createdAtEpochMs: Long? = null,
    /** Set when the author edits the comment; absent on a never-edited comment. */
    val editedAtEpochMs: Long? = null,
    /** Advisory: account uids `@mentioned` in [text] (capped at 10 upstream). `null`/absent = none. */
    val mentions: List<String>? = null,
    /**
     * Display-name snapshot at post time (mirrors [es.schsebastian.foodrats.feature.meal.data.firebase.MealDto]'s
     * `authorName`). Server-push rendering only — never mapped into the domain [es.schsebastian.foodrats.core.domain.meal.MealComment]
     * (client rendering resolves live author names via `AccountReadPort`).
     */
    val authorName: String? = null,
)
