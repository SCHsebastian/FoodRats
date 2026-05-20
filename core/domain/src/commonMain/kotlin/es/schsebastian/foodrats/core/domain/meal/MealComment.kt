package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline
value class MealCommentId(val value: String)

data class CommentAuthor(
    val accountId: AccountId,
    val displayName: String,
    val avatarUrl: String?,
)

data class MealComment(
    val id: MealCommentId,
    val mealId: MealId,
    val crewId: CrewId,
    val author: CommentAuthor,
    val text: CommentText,
    val createdAt: Instant,
)
