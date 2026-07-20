package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.first

/**
 * Edits a comment's text. The caller (ViewModel) has already validated the [CommentText] and screened
 * it through the on-device text filter — this use case only routes the write. Authorization
 * (author-only) is enforced server-side by the Firestore rules; a rejection surfaces as
 * [CommentError.Edit.NotAuthor]. The UI gates the affordance with the same check.
 *
 * OFFLINE-FIRST (P2 §0.5). The online success path is unchanged. When the device is offline — or the
 * direct edit fails with a connectivity-class error ([CommentError.Edit.Unavailable]) — the command
 * is durably parked in the [OutboxPort] and the use case returns [Result.Ok]; the `OutboxRunner`
 * replays it (idempotently — re-applying the same text is a no-op) when connectivity returns.
 */
class EditCommentUseCase(
    private val comments: MealCommentPort,
    private val connectivity: ConnectivityPort,
    private val outbox: OutboxPort,
) {
    suspend operator fun invoke(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId> = emptyList(),
    ): Result<Unit, CommentError.Edit> {
        if (!connectivity.isOnline().first()) {
            return enqueue(crewId, mealId, commentId, text, mentions)
        }
        return when (val r = comments.edit(crewId, mealId, commentId, text, mentions)) {
            is Result.Ok -> r
            is Result.Err -> when (r.error) {
                CommentError.Edit.Unavailable -> enqueue(crewId, mealId, commentId, text, mentions)
                else -> r
            }
        }
    }

    private suspend fun enqueue(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId>,
    ): Result<Unit, CommentError.Edit> =
        // A failed durable write means the edit never entered the outbox — no replay will
        // ever happen. Reporting success here would silently drop the edit (the same
        // silent-drop class RateMealUseCase and the upload coordinator already guard).
        when (outbox.enqueue(PendingCommand.EditComment(crewId, mealId, commentId, text, mentions))) {
            is Result.Ok -> Result.success(Unit)
            is Result.Err -> Result.failure(CommentError.Edit.Unavailable)
        }
}
