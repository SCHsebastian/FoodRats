package es.schsebastian.foodrats.feature.meal.data.outbox

import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Replays the meal-bounded-context outbox commands (offline-first P2 §1 T6) —
 * [PendingCommand.RateMeal], [PendingCommand.PostComment],
 * [PendingCommand.DeleteComment], [PendingCommand.ToggleReaction] — against the
 * `:core:domain` write ports ([MealRatingPort], [MealCommentPort],
 * [MealReactionPort]).
 *
 * The cross-feature `OutboxRunner` lives in `:core:data`, which must NEVER import a
 * `:feature:*` module, so this handler (which DOES know the meal ports) lives here
 * and is contributed to the runner via Koin `getAll()` (`single<OutboxCommandHandler>`
 * in `mealModule`). The crew-admin commands are handled by the sibling
 * `CrewOutboxCommandHandler` in `:feature:crew`.
 *
 * Every [execute] is idempotent (the contract the runner relies on): a command
 * already applied on a prior attempt — or directly online before going offline —
 * resolves to [OutboxExecuteResult.AlreadyApplied] (treated as success), never a
 * failure. Transient backend/connectivity errors map to
 * [OutboxExecuteResult.Retryable] (the runner backs off and re-attempts);
 * permanent authorization / not-found / invalid-input errors map to
 * [OutboxExecuteResult.Terminal] (surfaced to the user, no further attempts).
 */
class MealOutboxCommandHandler(
    private val rating: MealRatingPort,
    private val comments: MealCommentPort,
    private val reactions: MealReactionPort,
    private val optimistic: OptimisticMealWritePort,
) : OutboxCommandHandler {

    override fun handles(cmd: PendingCommand): Boolean = when (cmd) {
        is PendingCommand.RateMeal,
        is PendingCommand.PostComment,
        is PendingCommand.DeleteComment,
        is PendingCommand.ToggleReaction -> true
        is PendingCommand.RenameCrew,
        is PendingCommand.SetBlindVoting,
        is PendingCommand.RemoveMember,
        is PendingCommand.LeaveCrew -> false
    }

    override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult = when (cmd) {
        is PendingCommand.RateMeal -> rate(cmd)
        is PendingCommand.PostComment -> post(cmd)
        is PendingCommand.DeleteComment -> delete(cmd)
        is PendingCommand.ToggleReaction -> toggleReaction(cmd)
        // Not ours — [handles] returns false for these, so the runner never routes
        // them here. Mapped to a terminal so an accidental dispatch can't loop.
        is PendingCommand.RenameCrew,
        is PendingCommand.SetBlindVoting,
        is PendingCommand.RemoveMember,
        is PendingCommand.LeaveCrew -> OutboxExecuteResult.Terminal("outbox.error.wrongHandler")
    }

    /**
     * When a [PendingCommand.RateMeal] command reaches a terminal state (permanent
     * failure like [RateError.Unauthorized] / [RateError.CannotRateOwnMeal] /
     * [RateError.RatingWindowClosed], or exhausted retry budget), the phantom
     * optimistic star that was written before enqueue must be rolled back. Calls
     * [OptimisticMealWritePort.clearPending] which is idempotent — safe to call even
     * if the server snapshot already reconciled the row.
     */
    override suspend fun onTerminal(command: PendingCommand) {
        if (command is PendingCommand.RateMeal) {
            optimistic.clearPending(command.idempotencyKey)
        }
    }

    private suspend fun rate(cmd: PendingCommand.RateMeal): OutboxExecuteResult =
        when (val r = rating.rate(cmd.crewId, cmd.mealId, cmd.raterId, cmd.score)) {
            is Result.Ok -> OutboxExecuteResult.Success
            is Result.Err -> when (r.error) {
                // Rating is idempotent (overwrites `ratings[uid]`); a replay that the
                // backstop reports as already-rated is the goal already met.
                RateError.AlreadyRated -> OutboxExecuteResult.AlreadyApplied
                // Transient — back off and retry.
                RateError.Offline,
                RateError.RateUnavailable -> OutboxExecuteResult.Retryable("meal.error.rateUnavailable")
                // Permanent — retrying cannot fix it.
                RateError.Unauthorized -> OutboxExecuteResult.Terminal("meal.error.rateUnauthorized")
                RateError.CannotRateOwnMeal -> OutboxExecuteResult.Terminal("meal.error.rateOwnMeal")
                RateError.RatingWindowClosed -> OutboxExecuteResult.Terminal("meal.error.rateWindowClosed")
            }
        }

    private suspend fun post(cmd: PendingCommand.PostComment): OutboxExecuteResult =
        when (val r = comments.post(cmd.crewId, cmd.mealId, cmd.commentId, cmd.text)) {
            is Result.Ok -> OutboxExecuteResult.Success
            is Result.Err -> when (r.error) {
                // Transient — back off and retry.
                CommentError.Write.Unavailable -> OutboxExecuteResult.Retryable("meal.error.commentUnavailable")
                // Permanent — retrying cannot fix it.
                CommentError.Write.Unauthorized -> OutboxExecuteResult.Terminal("meal.error.commentUnauthorized")
                CommentError.Write.Blank -> OutboxExecuteResult.Terminal("meal.error.commentBlank")
                CommentError.Write.TooLong -> OutboxExecuteResult.Terminal("meal.error.commentTooLong")
                // Hard-blocked before enqueue by the on-device text filter (UGC §3), so this never
                // reaches the outbox; permanent (re-screening can't change the verdict) if it ever did.
                CommentError.Write.Objectionable -> OutboxExecuteResult.Terminal("meal.error.commentObjectionable")
            }
        }

    private suspend fun delete(cmd: PendingCommand.DeleteComment): OutboxExecuteResult =
        when (val r = comments.delete(cmd.crewId, cmd.mealId, cmd.commentId)) {
            is Result.Ok -> OutboxExecuteResult.Success
            is Result.Err -> when (r.error) {
                // Deleting an absent doc is the goal already met (idempotent delete).
                CommentError.Delete.NotFound -> OutboxExecuteResult.AlreadyApplied
                // Transient — back off and retry.
                CommentError.Delete.Unavailable -> OutboxExecuteResult.Retryable("meal.error.commentDeleteUnavailable")
                // Permanent — retrying cannot fix it.
                CommentError.Delete.NotAuthorOrOwner -> OutboxExecuteResult.Terminal("meal.error.commentDeleteNotAuthorOrOwner")
            }
        }

    /**
     * Converge the member's reaction to the absolute [PendingCommand.ToggleReaction.desiredPresent]
     * target rather than blindly re-applying a relative flip — so a replay is idempotent regardless
     * of the server's current state. The port only exposes a relative [MealReactionPort.toggle], so:
     *  - the first toggle reports the resulting presence ([ReactionToggle.Added] = now present,
     *    [ReactionToggle.Removed] = now absent);
     *  - if that already matches the target → done;
     *  - otherwise toggle once more to land on the target (at most two calls).
     *
     * An unknown persisted [PendingCommand.ToggleReaction.reactionKindKey] (a kind a newer build
     * wrote) can't be replayed by this build — terminal so the user can dismiss it.
     */
    private suspend fun toggleReaction(cmd: PendingCommand.ToggleReaction): OutboxExecuteResult {
        val kind = ReactionKind.fromKey(cmd.reactionKindKey)
            ?: return OutboxExecuteResult.Terminal("meal.error.reactionUnknownKind")

        return when (val first = reactions.toggle(cmd.crewId, cmd.mealId, cmd.reactorId, kind)) {
            is Result.Err -> first.error.toExecuteResult()
            is Result.Ok -> {
                val presentNow = first.value == ReactionToggle.Added
                if (presentNow == cmd.desiredPresent) {
                    OutboxExecuteResult.Success
                } else {
                    // One more flip to converge onto the absolute target.
                    when (val second = reactions.toggle(cmd.crewId, cmd.mealId, cmd.reactorId, kind)) {
                        is Result.Ok -> OutboxExecuteResult.Success
                        is Result.Err -> second.error.toExecuteResult()
                    }
                }
            }
        }
    }

    private fun ReactionError.Toggle.toExecuteResult(): OutboxExecuteResult = when (this) {
        // Transient — back off and retry.
        ReactionError.Toggle.Offline,
        ReactionError.Toggle.Unavailable -> OutboxExecuteResult.Retryable("meal.error.reactionUnavailable")
        // Permanent — retrying cannot fix it.
        ReactionError.Toggle.Unauthorized -> OutboxExecuteResult.Terminal("meal.error.reactionUnauthorized")
        ReactionError.Toggle.MealNotFound -> OutboxExecuteResult.Terminal("meal.error.reactionMealNotFound")
    }
}
