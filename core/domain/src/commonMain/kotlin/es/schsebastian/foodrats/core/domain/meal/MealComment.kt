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
    /**
     * Account uids of crew members `@mentioned` in [text] (advisory — parsed client-side from
     * `@handle` tokens, capped at 10, extras silently ignored). Never validated server-side beyond
     * membership; drives the mention-push fan-out and `@token` highlight, nothing else.
     */
    val mentions: List<AccountId> = emptyList(),
)
