package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant

fun CommentDto.toDomain(crewId: CrewId, mealId: MealId): Result<MealComment, CommentError.Read> {
    val id = this.id ?: return Result.failure(CommentError.Read.Unavailable)
    val accountId = (AccountId.of(authorId.orEmpty()) as? Result.Ok)?.value
        ?: return Result.failure(CommentError.Read.Unavailable)
    val text = (CommentText.of(this.text.orEmpty()) as? Result.Ok)?.value
        ?: return Result.failure(CommentError.Read.Unavailable)
    return Result.success(
        MealComment(
            id = MealCommentId(id),
            mealId = mealId,
            crewId = crewId,
            authorId = accountId,
            text = text,
            createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs ?: 0L),
            editedAt = editedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
            // null/absent = no mentions; blank/invalid uids are dropped (same tolerance as authorId).
            mentions = mentions.orEmpty().mapNotNull { raw -> (AccountId.of(raw) as? Result.Ok)?.value },
        )
    )
}
