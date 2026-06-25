package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class MealCommentId(val value: String)

data class MealComment(
    val id: MealCommentId,
    val mealId: MealId,
    val crewId: CrewId,
    val authorId: AccountId,
    val text: CommentText,
    val createdAt: Instant,
    /** When the author last edited the text; `null` for a never-edited comment. Drives the "edited" tag. */
    val editedAt: Instant? = null,
)
