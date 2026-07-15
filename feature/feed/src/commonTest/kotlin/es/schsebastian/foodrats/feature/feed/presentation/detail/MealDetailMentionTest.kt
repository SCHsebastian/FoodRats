package es.schsebastian.foodrats.feature.feed.presentation.detail

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Records every [post]/[edit] call (including the resolved [AccountId] mentions) — the assertion
 *  surface for [MealDetailMentionTest]. */
private class RecordingMentionCommentPort : MealCommentPort {
    data class PostCall(val commentId: String, val text: String, val mentions: List<AccountId>)
    data class EditCall(val commentId: String, val text: String, val mentions: List<AccountId>)

    private val flow = MutableStateFlow<Result<List<MealComment>, CommentError.Read>>(Result.success(emptyList()))
    val postCalls = mutableListOf<PostCall>()
    val editCalls = mutableListOf<EditCall>()

    override fun observe(crewId: CrewId, mealId: MealId, limit: Int): Flow<Result<List<MealComment>, CommentError.Read>> = flow

    override suspend fun post(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId>,
    ): Result<Unit, CommentError.Write> {
        postCalls += PostCall(commentId.value, text.value, mentions)
        return Result.success(Unit)
    }

    override suspend fun edit(
        crewId: CrewId,
        mealId: MealId,
        commentId: MealCommentId,
        text: CommentText,
        mentions: List<AccountId>,
    ): Result<Unit, CommentError.Edit> {
        editCalls += EditCall(commentId.value, text.value, mentions)
        return Result.success(Unit)
    }

    override suspend fun delete(crewId: CrewId, mealId: MealId, commentId: MealCommentId): Result<Unit, CommentError.Delete> =
        Result.success(Unit)

    fun emit(comments: List<MealComment>) {
        flow.value = Result.success(comments)
    }
}

/**
 * @-mentions: composer suggestion state (typing/picking) + mention resolution on post/edit
 * (online, offline-outbox, and unknown-token cases). [MentionParser] itself is unit-tested
 * separately — this file exercises its wiring into [MealDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailMentionTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val sebasId = (AccountId.of("u-sebas") as Result.Ok).value
    private val liaId = (AccountId.of("u-lia") as Result.Ok).value
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-20T12:00:00Z") }

    private class Ports(
        val commentPort: RecordingMentionCommentPort,
        val outbox: RecordingOutboxPort,
        val connectivity: FakeConnectivityPort,
    )

    private fun newSut(online: Boolean = true): Pair<MealDetailViewModel, Ports> {
        val commentPort = RecordingMentionCommentPort()
        val accountPort = FakeAccountReadPort()
        accountPort.set(sebasId, Account(sebasId, "sebas", "Sebas C", null, null))
        accountPort.set(liaId, Account(liaId, "lia", "Lia G", null, null))
        val crewRoster = FakeCrewRosterPort(listOf(sebasId, liaId, viewerId))
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = online)
        val outbox = RecordingOutboxPort()
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, FakeMealReadPort(), session, FakeBlockedAccountsPort()),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox, RecordingOptimisticMealWritePort()),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = accountPort,
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
            crewOwner = FakeCrewOwnerPort(),
            storyShareController = RecordingStoryShareController(),
            crewRoster = crewRoster,
        )
        return vm to Ports(commentPort, outbox, connectivity)
    }

    // --- composer suggestion state -----------------------------------------------------------

    @Test fun typing_a_trailing_fragment_surfaces_matching_roster_candidates() = runTest {
        val (vm, _) = newSut()
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey @se"))
        runCurrent()
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(listOf(MentionSuggestionUi(sebasId.value, "sebas", "Sebas C")), s.mentionSuggestions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun no_trailing_fragment_means_no_suggestions() = runTest {
        val (vm, _) = newSut()
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey there"))
        runCurrent()
        vm.state.test {
            assertTrue(expectMostRecentItem().mentionSuggestions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun picking_a_suggestion_rewrites_the_trailing_fragment_and_clears_suggestions() = runTest {
        val (vm, _) = newSut()
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey @se"))
        runCurrent()

        vm.onIntent(MealDetailIntent.MentionSuggestionPicked("sebas"))
        runCurrent()

        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals("hey @sebas ", s.commentInput)
            assertTrue(s.mentionSuggestions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- postComment / submitEditComment mention resolution ----------------------------------

    @Test fun post_comment_resolves_and_passes_mentions_to_the_port() = runTest {
        val (vm, ports) = newSut()
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey @sebas thanks"))
        vm.onIntent(MealDetailIntent.PostComment)
        runCurrent()

        assertEquals(1, ports.commentPort.postCalls.size)
        assertEquals(listOf(sebasId), ports.commentPort.postCalls.single().mentions)
    }

    @Test fun post_comment_with_unknown_token_resolves_no_mentions() = runTest {
        val (vm, ports) = newSut()
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey @ghost"))
        vm.onIntent(MealDetailIntent.PostComment)
        runCurrent()

        assertEquals(1, ports.commentPort.postCalls.size)
        assertTrue(ports.commentPort.postCalls.single().mentions.isEmpty())
    }

    @Test fun offline_post_enqueues_pending_command_with_resolved_mentions() = runTest {
        val (vm, ports) = newSut(online = false)
        vm.onIntent(MealDetailIntent.CommentInputChanged("hey @lia"))
        vm.onIntent(MealDetailIntent.PostComment)
        runCurrent()

        assertTrue(ports.commentPort.postCalls.isEmpty())
        val enqueued = ports.outbox.enqueued.single() as PendingCommand.PostComment
        assertEquals(listOf(liaId), enqueued.mentions)
    }

    @Test fun submit_edit_comment_reparses_mentions_from_the_edited_text() = runTest {
        val (vm, ports) = newSut()
        val comment = MealComment(
            id = MealCommentId("cmt-1"),
            mealId = (MealId.of("meal-1") as Result.Ok).value,
            crewId = crew,
            authorId = viewerId,
            text = (CommentText.of("original") as Result.Ok).value,
            createdAt = Instant.parse("2026-05-20T11:59:00Z"),
        )
        ports.commentPort.emit(listOf(comment))
        runCurrent()

        vm.onIntent(MealDetailIntent.StartEditComment(MealCommentId("cmt-1")))
        vm.onIntent(MealDetailIntent.EditCommentInputChanged("edited @lia"))
        vm.onIntent(MealDetailIntent.SubmitEditComment)
        runCurrent()

        assertEquals(1, ports.commentPort.editCalls.size)
        assertEquals(listOf(liaId), ports.commentPort.editCalls.single().mentions)
    }
}
