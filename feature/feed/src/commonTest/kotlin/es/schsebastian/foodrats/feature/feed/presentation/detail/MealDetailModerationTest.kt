package es.schsebastian.foodrats.feature.feed.presentation.detail

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.designsystem.molecules.FrReportReasonOption
import es.schsebastian.foodrats.core.domain.account.BlockError
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.moderation.ReportError
import es.schsebastian.foodrats.core.domain.moderation.ReportPort
import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget
import es.schsebastian.foodrats.core.domain.moderation.TextModerationPort
import es.schsebastian.foodrats.core.domain.moderation.TextModerationVerdict
import es.schsebastian.foodrats.core.domain.moderation.WordlistTextModeration
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.EditCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeConnectivityPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeMealReadPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RecordingOptimisticMealWritePort
import es.schsebastian.foodrats.feature.feed.domain.usecase.RecordingOutboxPort
import es.schsebastian.foodrats.feature.feed.presentation.feed.FakeCrewBlindVotingPort
import es.schsebastian.foodrats.feature.feed.presentation.feed.FakeMealRatingPort
import es.schsebastian.foodrats.feature.feed.presentation.feed.FakeSessionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Behavioral tests for the UGC compliance paths in [MealDetailViewModel]:
 *  - OpenReport / SubmitReport across all three targets (meal / comment / account)
 *  - BlockAuthor (meal-author path) vs BlockCommentAuthor (comment-author path)
 *  - Comment HARD-BLOCK: objectionable text rejected before outbox / Firestore
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailModerationTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val authorId = (AccountId.of("u-author") as Result.Ok).value
    private val day = MealDay(LocalDate.parse("2026-05-20"), zone)
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-20T12:00:00Z") }

    /**
     * Recording [ReportPort] fake: records every report call and always returns success. Test can
     * swap [nextResult] to a failure to exercise the error path.
     */
    class RecordingReportPort : ReportPort {
        data class ReportCall(val reporter: AccountId, val target: ReportTarget, val reason: ReportReason)

        val calls = mutableListOf<ReportCall>()
        var nextResult: Result<Unit, ReportError> = Result.success(Unit)

        override suspend fun report(
            reporter: AccountId,
            target: ReportTarget,
            reason: ReportReason,
        ): Result<Unit, ReportError> {
            calls += ReportCall(reporter, target, reason)
            return nextResult
        }
    }

    /**
     * Recording [BlockedAccountsPort] fake: records block calls and always returns success.
     */
    class RecordingBlockedAccountsPort : BlockedAccountsPort {
        data class BlockCall(val owner: AccountId, val target: AccountId)

        val blockCalls = mutableListOf<BlockCall>()
        var nextBlockResult: Result<Unit, BlockError> = Result.success(Unit)

        override fun observeBlocked(owner: AccountId): Flow<Set<AccountId>> = flowOf(emptySet())
        override suspend fun block(owner: AccountId, target: AccountId): Result<Unit, BlockError> {
            blockCalls += BlockCall(owner, target)
            return nextBlockResult
        }
        override suspend fun unblock(owner: AccountId, target: AccountId): Result<Unit, BlockError> =
            Result.success(Unit)
    }

    private fun mealBy(author: AccountId = authorId) = Meal(
        id = (MealId.of("meal-1") as Result.Ok).value,
        author = MealAuthor(author, "Author", null),
        crewId = crew,
        day = day,
        slot = MealSlot.Lunch,
        photoUrl = "https://x/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-05-20T10:00:00Z"),
    )

    private fun comment(authorRaw: String = "u-other", body: String = "hi"): MealComment {
        val aid = (AccountId.of(authorRaw) as Result.Ok).value
        return MealComment(
            id = MealCommentId("cmt-1"),
            mealId = (MealId.of("meal-1") as Result.Ok).value,
            crewId = crew,
            authorId = aid,
            text = (CommentText.of(body) as Result.Ok).value,
            createdAt = Instant.parse("2026-05-20T11:00:00Z"),
        )
    }

    private fun buildVm(
        reportPort: RecordingReportPort = RecordingReportPort(),
        blockedAccounts: RecordingBlockedAccountsPort = RecordingBlockedAccountsPort(),
        commentPort: FakeMealCommentPort = FakeMealCommentPort(),
        textModeration: TextModerationPort = TextModerationPort { _, _ -> TextModerationVerdict.Clean },
        mealAuthor: AccountId = authorId,
    ): Triple<MealDetailViewModel, RecordingReportPort, RecordingBlockedAccountsPort> {
        val readPort = FakeMealReadPort(
            perDay = mapOf((crew to day.toKey()) to listOf(MealWithRatings(mealBy(mealAuthor), emptyList()))),
        )
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()

        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, readPort, session, FakeBlockedAccountsPort()),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox, RecordingOptimisticMealWritePort()),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = FakeAccountReadPort(),
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = clock,
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(
                FakeMealDeletePort(),
                FakeCrewMembership(),
            ),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            editComment = EditCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(null),
            storyShareController = RecordingStoryShareController(),
            reportPort = reportPort,
            blockedAccounts = blockedAccounts,
            textModeration = textModeration,
        )
        return Triple(vm, reportPort, blockedAccounts)
    }

    // ─── OpenReport / SubmitReport ─────────────────────────────────────────────

    @Test fun open_report_sets_target_in_state() = runTest {
        val (vm, _, _) = buildVm()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Meal))
        runCurrent()
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(ReportTargetUi.Meal, s.reportTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun submit_report_meal_calls_port_with_meal_target() = runTest {
        val reporter = RecordingReportPort()
        val (vm, _, _) = buildVm(reportPort = reporter)
        // Allow init coroutines to settle (populate matchedMeal) before submitting.
        runCurrent()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Meal))
        runCurrent()
        vm.onIntent(MealDetailIntent.SubmitReport(FrReportReasonOption.SPAM))
        runCurrent()

        assertEquals(1, reporter.calls.size)
        val call = reporter.calls.single()
        assertTrue(call.target is ReportTarget.Meal)
        assertEquals(ReportReason.Spam, call.reason)
        assertEquals(viewerId, call.reporter)
    }

    @Test fun submit_report_author_calls_port_with_account_target() = runTest {
        val reporter = RecordingReportPort()
        val (vm, _, _) = buildVm(reportPort = reporter)
        runCurrent()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Author))
        runCurrent()
        vm.onIntent(MealDetailIntent.SubmitReport(FrReportReasonOption.HARASSMENT))
        runCurrent()

        assertEquals(1, reporter.calls.size)
        val call = reporter.calls.single()
        assertTrue(call.target is ReportTarget.Account)
        assertEquals(authorId, (call.target as ReportTarget.Account).accountId)
        assertEquals(ReportReason.Harassment, call.reason)
    }

    @Test fun submit_report_comment_calls_port_with_comment_target() = runTest {
        val reporter = RecordingReportPort()
        val commentId = MealCommentId("cmt-1")
        val (vm, _, _) = buildVm(reportPort = reporter)
        runCurrent()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Comment(commentId)))
        runCurrent()
        vm.onIntent(MealDetailIntent.SubmitReport(FrReportReasonOption.HATE))
        runCurrent()

        assertEquals(1, reporter.calls.size)
        val call = reporter.calls.single()
        assertTrue(call.target is ReportTarget.Comment)
        assertEquals(ReportReason.Hate, call.reason)
    }

    @Test fun successful_report_sets_reportSuccess_and_clears_target() = runTest {
        val (vm, _, _) = buildVm()
        runCurrent()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Meal))
        runCurrent()
        vm.onIntent(MealDetailIntent.SubmitReport(FrReportReasonOption.SPAM))
        runCurrent()

        vm.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.reportSuccess)
            assertNull(s.reportTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun failed_report_sets_reportError_and_does_not_set_reportSuccess() = runTest {
        val reporter = RecordingReportPort()
        reporter.nextResult = Result.failure(ReportError.Submit.Unavailable)
        val (vm, _, _) = buildVm(reportPort = reporter)
        runCurrent()
        vm.onIntent(MealDetailIntent.OpenReport(ReportTargetUi.Meal))
        runCurrent()
        vm.onIntent(MealDetailIntent.SubmitReport(FrReportReasonOption.SPAM))
        runCurrent()

        vm.state.test {
            val s = expectMostRecentItem()
            assertFalse(s.reportSuccess)
            assertNotNull(s.reportError)
            assertNotNull(s.reportTarget) // sheet stays open on error
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── BlockAuthor vs BlockCommentAuthor ─────────────────────────────────────

    @Test fun block_author_intent_calls_block_port_with_meal_author_id() = runTest {
        val blocks = RecordingBlockedAccountsPort()
        val (vm, _, _) = buildVm(blockedAccounts = blocks)
        // Allow init coroutines to complete (populate matchedMeal) before dispatching BlockAuthor.
        runCurrent()
        vm.onIntent(MealDetailIntent.BlockAuthor)
        runCurrent()

        // BlockAuthor delegates to blockAccount(authorId.value) which calls block(viewer, author).
        assertEquals(1, blocks.blockCalls.size)
        assertEquals(viewerId, blocks.blockCalls.single().owner)
        assertEquals(authorId, blocks.blockCalls.single().target)

        vm.state.test {
            assertTrue(expectMostRecentItem().blockSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun block_comment_author_intent_calls_block_port_with_comment_author_id() = runTest {
        val commentAuthorId = (AccountId.of("u-commenter") as Result.Ok).value
        val blocks = RecordingBlockedAccountsPort()
        val (vm, _, _) = buildVm(blockedAccounts = blocks)
        runCurrent()
        vm.onIntent(MealDetailIntent.BlockCommentAuthor(commentAuthorId.value))
        runCurrent()

        assertEquals(1, blocks.blockCalls.size)
        assertEquals(viewerId, blocks.blockCalls.single().owner)
        assertEquals(commentAuthorId, blocks.blockCalls.single().target)

        vm.state.test {
            assertTrue(expectMostRecentItem().blockSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun block_author_and_block_comment_author_are_independent_paths() = runTest {
        // Ensure that dispatching BlockAuthor does NOT reach the BlockCommentAuthor code
        // (and vice versa), so neither is dead code.
        val blocks = RecordingBlockedAccountsPort()
        val (vm, _, _) = buildVm(blockedAccounts = blocks)
        runCurrent()
        vm.onIntent(MealDetailIntent.BlockAuthor)
        runCurrent()

        val otherAuthor = (AccountId.of("u-other") as Result.Ok).value
        vm.onIntent(MealDetailIntent.BlockCommentAuthor(otherAuthor.value))
        runCurrent()

        assertEquals(2, blocks.blockCalls.size)
        // First call → meal author; second call → comment author (distinct ids).
        assertEquals(authorId, blocks.blockCalls[0].target)
        assertEquals((AccountId.of("u-other") as Result.Ok).value, blocks.blockCalls[1].target)
    }

    // ─── Comment HARD-BLOCK (objectionable text) ───────────────────────────────

    @Test fun objectionable_comment_is_rejected_before_port_online() = runTest {
        val commentPort = FakeMealCommentPort()
        val outbox = RecordingOutboxPort()
        val connectivity = FakeConnectivityPort(online = true)
        // Use the real WordlistTextModeration so this is genuinely content-driven.
        val moderation: TextModerationPort = WordlistTextModeration()

        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(
                active,
                FakeMealReadPort(perDay = mapOf((crew to day.toKey()) to listOf(MealWithRatings(mealBy(), emptyList())))),
                session,
                FakeBlockedAccountsPort(),
            ),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox, RecordingOptimisticMealWritePort()),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = FakeAccountReadPort(),
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = clock,
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(FakeMealDeletePort(), FakeCrewMembership()),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            editComment = EditCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(null),
            storyShareController = RecordingStoryShareController(),
            textModeration = moderation,
        )

        // Type an objectionable word and attempt to post.
        vm.onIntent(MealDetailIntent.CommentInputChanged("fuck"))
        runCurrent()
        vm.onIntent(MealDetailIntent.PostComment)
        runCurrent()

        vm.state.test {
            val s = expectMostRecentItem()
            // Must be blocked before reaching the port.
            assertEquals(CommentError.Write.Objectionable, s.commentWriteError)
            // The comment text is still in the input — not cleared.
            assertEquals("fuck", s.commentInput)
            cancelAndIgnoreRemainingEvents()
        }
        // Outbox must be empty — objectionable comment must not have been enqueued (neither online port
        // nor outbox path may receive the content; the outbox check covers both paths).
        assertTrue(outbox.enqueued.isEmpty(), "outbox must be empty: objectionable content is rejected before any write")
    }

    @Test fun objectionable_comment_is_rejected_before_outbox_offline() = runTest {
        val commentPort = FakeMealCommentPort()
        val outbox = RecordingOutboxPort()
        // Force offline so the VM would normally take the outbox path.
        val connectivity = FakeConnectivityPort(online = false)
        val moderation: TextModerationPort = WordlistTextModeration()

        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(
                active,
                FakeMealReadPort(perDay = mapOf((crew to day.toKey()) to listOf(MealWithRatings(mealBy(), emptyList())))),
                session,
                FakeBlockedAccountsPort(),
            ),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox, RecordingOptimisticMealWritePort()),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = FakeAccountReadPort(),
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = clock,
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(FakeMealDeletePort(), FakeCrewMembership()),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            editComment = EditCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(null),
            storyShareController = RecordingStoryShareController(),
            textModeration = moderation,
        )

        vm.onIntent(MealDetailIntent.CommentInputChanged("fuck"))
        runCurrent()
        vm.onIntent(MealDetailIntent.PostComment)
        runCurrent()

        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(CommentError.Write.Objectionable, s.commentWriteError)
            cancelAndIgnoreRemainingEvents()
        }
        // Even in offline mode, the outbox must NOT receive the objectionable comment.
        assertTrue(outbox.enqueued.isEmpty())
    }
}
