package es.schsebastian.foodrats.feature.meal.data.outbox

import es.schsebastian.foodrats.core.domain.account.DisplayName
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReactions
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.meal.ReactionError
import es.schsebastian.foodrats.core.domain.meal.ReactionKind
import es.schsebastian.foodrats.core.domain.meal.ReactionToggle
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MealOutboxCommandHandlerTest {

    // ---- test doubles -----------------------------------------------------------------

    private class FakeMealRatingPort : MealRatingPort {
        data class Call(val crewId: CrewId, val mealId: MealId, val raterId: AccountId, val score: Score)
        val calls = mutableListOf<Call>()
        var nextResult: Result<Unit, RateError> = Result.success(Unit)
        override suspend fun rate(
            crewId: CrewId,
            mealId: MealId,
            raterId: AccountId,
            score: Score,
        ): Result<Unit, RateError> {
            calls += Call(crewId, mealId, raterId, score)
            return nextResult
        }
    }

    private class FakeMealCommentPort : MealCommentPort {
        val postCalls = mutableListOf<Triple<CrewId, MealId, MealCommentId>>()
        val editCalls = mutableListOf<Triple<CrewId, MealId, MealCommentId>>()
        val deleteCalls = mutableListOf<Pair<CrewId, MealCommentId>>()
        var nextPostResult: Result<Unit, CommentError.Write> = Result.success(Unit)
        var nextEditResult: Result<Unit, CommentError.Edit> = Result.success(Unit)
        var nextDeleteResult: Result<Unit, CommentError.Delete> = Result.success(Unit)

        override fun observe(crewId: CrewId, mealId: MealId, limit: Int): Flow<Result<List<MealComment>, CommentError.Read>> =
            flowOf(Result.success(emptyList()))

        override suspend fun post(
            crewId: CrewId,
            mealId: MealId,
            commentId: MealCommentId,
            text: CommentText,
        ): Result<Unit, CommentError.Write> {
            postCalls += Triple(crewId, mealId, commentId)
            return nextPostResult
        }

        override suspend fun edit(
            crewId: CrewId,
            mealId: MealId,
            commentId: MealCommentId,
            text: CommentText,
        ): Result<Unit, CommentError.Edit> {
            editCalls += Triple(crewId, mealId, commentId)
            return nextEditResult
        }

        override suspend fun delete(
            crewId: CrewId,
            mealId: MealId,
            commentId: MealCommentId,
        ): Result<Unit, CommentError.Delete> {
            deleteCalls += crewId to commentId
            return nextDeleteResult
        }
    }

    private class FakeMealReactionPort : MealReactionPort {
        val toggleCalls = mutableListOf<Pair<CrewId, MealId>>()

        // Consumed in order, one per `toggle` call; falls back to `defaultResult` once drained —
        // lets a test script the two-call converge-to-target sequence exercised by [toggleReaction].
        private val queuedResults = ArrayDeque<Result<ReactionToggle, ReactionError.Toggle>>()
        var defaultResult: Result<ReactionToggle, ReactionError.Toggle> = Result.success(ReactionToggle.Added)

        fun enqueue(result: Result<ReactionToggle, ReactionError.Toggle>) {
            queuedResults += result
        }

        override fun observe(crewId: CrewId, mealId: MealId): Flow<Result<MealReactions, ReactionError.Read>> =
            flowOf(Result.success(MealReactions.empty(mealId)))

        override suspend fun toggle(
            crewId: CrewId,
            mealId: MealId,
            reactorId: AccountId,
            kind: ReactionKind,
        ): Result<ReactionToggle, ReactionError.Toggle> {
            toggleCalls += crewId to mealId
            return if (queuedResults.isNotEmpty()) queuedResults.removeFirst() else defaultResult
        }
    }

    private class FakeOptimisticMealWritePort : OptimisticMealWritePort {
        data class AppliedRate(val crewId: CrewId, val mealId: MealId, val raterId: AccountId, val score: Score, val idempotencyKey: String)
        val applied = mutableListOf<AppliedRate>()
        val cleared = mutableListOf<String>()

        override suspend fun applyRate(
            crewId: CrewId,
            mealId: MealId,
            raterId: AccountId,
            score: Score,
            idempotencyKey: String,
        ) {
            applied += AppliedRate(crewId, mealId, raterId, score, idempotencyKey)
        }

        override suspend fun clearPending(idempotencyKey: String) {
            cleared += idempotencyKey
        }
    }

    // ---- fixtures -----------------------------------------------------------------------

    private val crewId = (CrewId.of("crew-1") as Result.Ok).value
    private val mealId = (MealId.of("meal-1") as Result.Ok).value
    private val accountId = (AccountId.of("acc-1") as Result.Ok).value
    private val commentId = MealCommentId("comment-1")
    private val commentText = (CommentText.of("looks great") as Result.Ok).value
    private val score = (Score.of(4) as Result.Ok).value

    private val rateCmd = PendingCommand.RateMeal(crewId, mealId, accountId, score)
    private val postCmd = PendingCommand.PostComment(crewId, mealId, commentId, commentText, accountId)
    private val editCmd = PendingCommand.EditComment(crewId, mealId, commentId, commentText)
    private val deleteCmd = PendingCommand.DeleteComment(crewId, mealId, commentId)
    private val toggleCmd = PendingCommand.ToggleReaction(
        crewId = crewId,
        mealId = mealId,
        reactorId = accountId,
        reactionKindKey = ReactionKind.DailyGlyph.key,
        desiredPresent = true,
    )

    private class Fixture {
        val rating = FakeMealRatingPort()
        val comments = FakeMealCommentPort()
        val reactions = FakeMealReactionPort()
        val optimistic = FakeOptimisticMealWritePort()
        val handler = MealOutboxCommandHandler(rating, comments, reactions, optimistic)
    }

    // ---- handles() ------------------------------------------------------------------------

    @Test fun handles_every_meal_bounded_context_command_kind() {
        val h = Fixture().handler
        assertTrue(h.handles(rateCmd))
        assertTrue(h.handles(postCmd))
        assertTrue(h.handles(editCmd))
        assertTrue(h.handles(deleteCmd))
        assertTrue(h.handles(toggleCmd))
    }

    @Test fun does_not_handle_crew_or_account_commands() {
        val h = Fixture().handler
        assertFalse(h.handles(PendingCommand.LeaveCrew(crewId, accountId)))
        assertFalse(h.handles(PendingCommand.RenameCrew(crewId, accountId, "New name")))
        assertFalse(h.handles(PendingCommand.SetDisplayName(accountId, (DisplayName.of("Alice") as Result.Ok).value)))
    }

    @Test fun execute_on_a_command_it_does_not_handle_is_a_terminal_guard() = runTest {
        val h = Fixture().handler
        val result = h.execute(PendingCommand.LeaveCrew(crewId, accountId))
        assertIs<OutboxExecuteResult.Terminal>(result)
    }

    // ---- rate ------------------------------------------------------------------------------

    @Test fun rate_success_returns_success() = runTest {
        val f = Fixture()
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(rateCmd))
        assertEquals(listOf(FakeMealRatingPort.Call(crewId, mealId, accountId, score)), f.rating.calls)
    }

    @Test fun rate_already_rated_is_already_applied_idempotent_replay() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.AlreadyRated)
        assertEquals(OutboxExecuteResult.AlreadyApplied, f.handler.execute(rateCmd))
    }

    @Test fun rate_offline_is_retryable() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.Offline)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(rateCmd))
    }

    @Test fun rate_unavailable_is_retryable() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.RateUnavailable)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(rateCmd))
    }

    @Test fun rate_unauthorized_is_terminal() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.Unauthorized)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(rateCmd))
    }

    @Test fun rate_own_meal_is_terminal() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.CannotRateOwnMeal)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(rateCmd))
    }

    @Test fun rate_window_closed_is_terminal() = runTest {
        val f = Fixture()
        f.rating.nextResult = Result.failure(RateError.RatingWindowClosed)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(rateCmd))
    }

    // ---- comment: post ----------------------------------------------------------------------

    @Test fun post_comment_success_returns_success() = runTest {
        val f = Fixture()
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(postCmd))
        assertEquals(listOf(Triple(crewId, mealId, commentId)), f.comments.postCalls)
    }

    @Test fun post_comment_unavailable_is_retryable() = runTest {
        val f = Fixture()
        f.comments.nextPostResult = Result.failure(CommentError.Write.Unavailable)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(postCmd))
    }

    @Test fun post_comment_unauthorized_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextPostResult = Result.failure(CommentError.Write.Unauthorized)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(postCmd))
    }

    @Test fun post_comment_blank_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextPostResult = Result.failure(CommentError.Write.Blank)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(postCmd))
    }

    @Test fun post_comment_too_long_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextPostResult = Result.failure(CommentError.Write.TooLong)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(postCmd))
    }

    @Test fun post_comment_objectionable_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextPostResult = Result.failure(CommentError.Write.Objectionable)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(postCmd))
    }

    // ---- comment: edit ------------------------------------------------------------------------

    @Test fun edit_comment_success_returns_success() = runTest {
        val f = Fixture()
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(editCmd))
        assertEquals(listOf(Triple(crewId, mealId, commentId)), f.comments.editCalls)
    }

    @Test fun edit_comment_unavailable_is_retryable() = runTest {
        val f = Fixture()
        f.comments.nextEditResult = Result.failure(CommentError.Edit.Unavailable)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(editCmd))
    }

    @Test fun edit_comment_not_found_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextEditResult = Result.failure(CommentError.Edit.NotFound)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(editCmd))
    }

    @Test fun edit_comment_not_author_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextEditResult = Result.failure(CommentError.Edit.NotAuthor)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(editCmd))
    }

    // ---- comment: delete -----------------------------------------------------------------------

    @Test fun delete_comment_success_returns_success() = runTest {
        val f = Fixture()
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(deleteCmd))
        assertEquals(listOf(crewId to commentId), f.comments.deleteCalls)
    }

    @Test fun delete_comment_not_found_is_already_applied_idempotent_replay() = runTest {
        val f = Fixture()
        f.comments.nextDeleteResult = Result.failure(CommentError.Delete.NotFound)
        assertEquals(OutboxExecuteResult.AlreadyApplied, f.handler.execute(deleteCmd))
    }

    @Test fun delete_comment_unavailable_is_retryable() = runTest {
        val f = Fixture()
        f.comments.nextDeleteResult = Result.failure(CommentError.Delete.Unavailable)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(deleteCmd))
    }

    @Test fun delete_comment_not_author_or_owner_is_terminal() = runTest {
        val f = Fixture()
        f.comments.nextDeleteResult = Result.failure(CommentError.Delete.NotAuthorOrOwner)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(deleteCmd))
    }

    // ---- reaction toggle -----------------------------------------------------------------------

    @Test fun toggle_reaction_unknown_kind_is_terminal_without_calling_the_port() = runTest {
        val f = Fixture()
        val unknownKindCmd = toggleCmd.copy(reactionKindKey = "some_future_kind")
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(unknownKindCmd))
        assertTrue(f.reactions.toggleCalls.isEmpty())
    }

    @Test fun toggle_reaction_first_flip_already_matches_target_returns_success_with_one_call() = runTest {
        val f = Fixture()
        // desiredPresent = true, first toggle reports Added (now present) → converged in one call.
        f.reactions.defaultResult = Result.success(ReactionToggle.Added)
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(toggleCmd))
        assertEquals(1, f.reactions.toggleCalls.size)
    }

    @Test fun toggle_reaction_first_flip_mismatched_target_converges_with_a_second_call() = runTest {
        val f = Fixture()
        // desiredPresent = true, but the member's existing reaction gets REMOVED by the first
        // (relative) toggle — the handler must flip once more to converge onto the target.
        f.reactions.enqueue(Result.success(ReactionToggle.Removed))
        f.reactions.enqueue(Result.success(ReactionToggle.Added))
        assertEquals(OutboxExecuteResult.Success, f.handler.execute(toggleCmd))
        assertEquals(2, f.reactions.toggleCalls.size)
    }

    @Test fun toggle_reaction_offline_is_retryable() = runTest {
        val f = Fixture()
        f.reactions.defaultResult = Result.failure(ReactionError.Toggle.Offline)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(toggleCmd))
    }

    @Test fun toggle_reaction_unavailable_is_retryable() = runTest {
        val f = Fixture()
        f.reactions.defaultResult = Result.failure(ReactionError.Toggle.Unavailable)
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(toggleCmd))
    }

    @Test fun toggle_reaction_unauthorized_is_terminal() = runTest {
        val f = Fixture()
        f.reactions.defaultResult = Result.failure(ReactionError.Toggle.Unauthorized)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(toggleCmd))
    }

    @Test fun toggle_reaction_meal_not_found_is_terminal() = runTest {
        val f = Fixture()
        f.reactions.defaultResult = Result.failure(ReactionError.Toggle.MealNotFound)
        assertIs<OutboxExecuteResult.Terminal>(f.handler.execute(toggleCmd))
    }

    @Test fun toggle_reaction_error_on_second_converging_flip_is_classified_too() = runTest {
        val f = Fixture()
        f.reactions.enqueue(Result.success(ReactionToggle.Removed))
        f.reactions.enqueue(Result.failure(ReactionError.Toggle.Unavailable))
        assertIs<OutboxExecuteResult.Retryable>(f.handler.execute(toggleCmd))
        assertEquals(2, f.reactions.toggleCalls.size)
    }

    // ---- onTerminal ----------------------------------------------------------------------------

    @Test fun on_terminal_for_rate_command_clears_the_optimistic_pending_star() = runTest {
        val f = Fixture()
        f.handler.onTerminal(rateCmd)
        assertEquals(listOf(rateCmd.idempotencyKey), f.optimistic.cleared)
    }

    @Test fun on_terminal_for_non_rate_commands_does_not_touch_the_optimistic_port() = runTest {
        val f = Fixture()
        f.handler.onTerminal(postCmd)
        f.handler.onTerminal(editCmd)
        f.handler.onTerminal(deleteCmd)
        f.handler.onTerminal(toggleCmd)
        assertTrue(f.optimistic.cleared.isEmpty())
    }
}
