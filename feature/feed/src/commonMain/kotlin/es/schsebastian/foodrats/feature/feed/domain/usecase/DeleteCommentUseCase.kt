package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.first

/**
 * Deletes a comment. Authorization (author OR crew owner) is enforced server-side by the
 * Firestore rules; a rejection surfaces as [CommentError.Delete.NotAuthorOrOwner]. The UI
 * gates the affordance with the same check.
 *
 * OFFLINE-FIRST (P2 §0.5). The online success path is unchanged. When the device is offline
 * — or the direct delete fails with a connectivity-class error ([CommentError.Delete.Unavailable])
 * — the command is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the
 * `OutboxRunner` replays it (idempotently — deleting an absent doc is a success) when
 * connectivity returns.
 */
class DeleteCommentUseCase(
    private val comments: MealCommentPort,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
    ): Result<Unit, CommentError.Delete> {
        if (!connectivity.isOnline().first()) {
            return enqueue(crewId, mealId, commentId)
        }
        return when (val r = comments.delete(crewId, mealId, commentId)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CommentError.Delete.Unavailable -> enqueue(crewId, mealId, commentId)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
    ): Result<Unit, CommentError.Delete> =
        // A failed durable write means the delete never entered the outbox — no replay will
        // ever happen. Reporting success here would silently drop the delete (the same
        // silent-drop class RateMealUseCase and the upload coordinator already guard).
        when (outbox.enqueue(PendingCommand.DeleteComment(crewId, mealId, commentId))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CommentError.Delete.Unavailable)
        }
}
