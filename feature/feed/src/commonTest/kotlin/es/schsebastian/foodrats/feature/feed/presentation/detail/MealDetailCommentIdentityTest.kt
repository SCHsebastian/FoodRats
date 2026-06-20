package es.schsebastian.foodrats.feature.feed.presentation.detail

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.account.Account
import es.schsebastian.foodrats.core.domain.meal.CommentError
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import kotlinx.coroutines.flow.flowOf
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeConnectivityPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeMealReadPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// A minimal fake comment port backed by a MutableStateFlow.
class FakeMealCommentPort : MealCommentPort {
    private val flow = MutableStateFlow<Result<List<MealComment>, CommentError.Read>>(
        Result.success(emptyList())
    )
    override fun observe(crewId: CrewId, mealId: MealId): Flow<Result<List<MealComment>, CommentError.Read>> = flow
    override suspend fun post(crewId: CrewId, mealId: MealId, commentId: MealCommentId, text: CommentText): Result<Unit, CommentError.Write> =
        Result.success(Unit)
    override suspend fun delete(crewId: CrewId, mealId: MealId, commentId: MealCommentId): Result<Unit, CommentError.Delete> =
        Result.success(Unit)

    fun emit(comments: List<MealComment>) {
        flow.value = Result.success(comments)
    }

    fun emitError(e: CommentError.Read) {
        flow.value = Result.failure(e)
    }
}

class FakeCrewOwnerPort(private val owner: AccountId? = null) : CrewOwnerPort {
    override fun observeOwner(crewId: CrewId) = flowOf(owner)
}

/** Crew-owner port whose owner can change at runtime, to exercise reactive RBAC. */
class MutableCrewOwnerPort(initial: AccountId? = null) : CrewOwnerPort {
    val owner = MutableStateFlow(initial)
    override fun observeOwner(crewId: CrewId): Flow<AccountId?> = owner
}

class FakeMealDeletePort : MealDeletePort {
    var result: Result<Unit, MealDeleteError> = Result.success(Unit)
    override suspend fun delete(crewId: CrewId, mealId: MealId) = result
    override suspend fun deleteFromAllCrews(
        crewIds: Set<CrewId>,
        authorId: AccountId,
        day: MealDay,
        slot: MealSlot,
    ) = result
}

class FakeCrewMembership(private val crews: List<CrewSummary> = emptyList()) : CrewMembershipPort {
    override fun observeMyCrews(accountId: AccountId): Flow<List<CrewSummary>> = flowOf(crews)
}

class FakeIngredientReadPort : IngredientReadPort {
    override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = flowOf(emptyMap())
    override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> = emptyList()
    override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> = emptyList()
}

data class TestPorts(
    val commentPort: FakeMealCommentPort,
    val accountPort: FakeAccountReadPort,
)

private class FixedClock(private val instant: Instant = Instant.parse("2026-05-20T12:00:00Z")) : Clock {
    override fun now() = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailCommentIdentityTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value

    private fun newSut(): Pair<MealDetailViewModel, TestPorts> {
        val commentPort = FakeMealCommentPort()
        val accountPort = FakeAccountReadPort()
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, FakeMealReadPort(), session, es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort()),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = accountPort,
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = FixedClock(),
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(FakeMealDeletePort(), FakeCrewMembership()),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(),
            storyShareController = es.schsebastian.foodrats.core.data.share.RecordingStoryShareController(),
        )
        return vm to TestPorts(commentPort, accountPort)
    }

    private fun accountId(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value

    private fun commentFrom(authorRaw: String, body: String): MealComment {
        val mealId = (MealId.of("meal-1") as Result.Ok).value
        val authorId = accountId(authorRaw)
        val text = (CommentText.of(body) as Result.Ok).value
        return MealComment(
            id = MealCommentId("cmt-$authorRaw"),
            mealId = mealId,
            crewId = crew,
            authorId = authorId,
            text = text,
            createdAt = Instant.parse("2026-05-20T11:59:00Z"),
        )
    }

    @Test fun resolves_displayName_and_avatar_from_account_doc() = runTest {
        val (vm, ports) = newSut()
        ports.commentPort.emit(listOf(commentFrom("user-1", "Hola")))
        ports.accountPort.set(accountId("user-1"), Account(accountId("user-1"), "h", "Sebas", null, "https://x/a.jpg"))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(1, s.commentRows.size)
            assertEquals("Sebas", s.commentRows[0].displayName)
            assertEquals("https://x/a.jpg", s.commentRows[0].avatarUrl)
            assertFalse(s.commentRows[0].loading)
            assertFalse(s.commentRows[0].isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun marks_row_deleted_when_account_doc_missing() = runTest {
        val (vm, ports) = newSut()
        ports.commentPort.emit(listOf(commentFrom("ghost", "...")))
        ports.accountPort.set(accountId("ghost"), null)
        vm.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.commentRows.single().isDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun dedupes_listeners_per_unique_author() = runTest {
        val (vm, ports) = newSut()
        ports.commentPort.emit(listOf(
            commentFrom("user-1", "a"),
            commentFrom("user-1", "b"),
            commentFrom("user-1", "c"),
        ))
        ports.accountPort.set(accountId("user-1"), Account(accountId("user-1"), "h", "Sebas", null, null))
        vm.state.test {
            expectMostRecentItem()
            assertEquals(1, ports.accountPort.observeCount[accountId("user-1")])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rename_propagates_without_comments_re_emitting() = runTest {
        val (vm, ports) = newSut()
        ports.commentPort.emit(listOf(commentFrom("user-1", "hey")))
        ports.accountPort.set(accountId("user-1"), Account(accountId("user-1"), "h", "Old", null, null))
        vm.state.test {
            assertEquals("Old", expectMostRecentItem().commentRows.single().displayName)
            ports.accountPort.set(accountId("user-1"), Account(accountId("user-1"), "h", "New", null, null))
            assertEquals("New", awaitItem().commentRows.single().displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun shows_only_user_confirmed_ingredients_not_ai_detected() = runTest {
        val day = MealDay(kotlinx.datetime.LocalDate.parse("2026-05-20"), zone)
        val meal = es.schsebastian.foodrats.core.domain.meal.Meal(
            id = (MealId.of("meal-1") as Result.Ok).value,
            author = es.schsebastian.foodrats.core.domain.meal.MealAuthor(
                (AccountId.of("u-author") as Result.Ok).value, "Author", null,
            ),
            crewId = crew,
            day = day,
            slot = es.schsebastian.foodrats.core.domain.meal.MealSlot.Lunch,
            photoUrl = "https://x/p.jpg",
            dish = (es.schsebastian.foodrats.core.domain.meal.DishName.of("Pasta") as Result.Ok).value,
            description = es.schsebastian.foodrats.core.domain.meal.Description.EMPTY,
            publishedAt = Instant.parse("2026-05-20T10:00:00Z"),
            ingredients = listOf(IngredientSlug.of("egg").getOrNull()!!),
            detectedIngredients = listOf(IngredientSlug.of("bacon").getOrNull()!!),
        )
        val readPort = FakeMealReadPort(
            perDay = mapOf((crew to day.toKey()) to listOf(
                es.schsebastian.foodrats.core.domain.meal.MealWithRatings(meal, emptyList()),
            )),
        )
        val commentPort = FakeMealCommentPort()
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, readPort, session, es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort()),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = FakeAccountReadPort(),
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = FixedClock(),
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(FakeMealDeletePort(), FakeCrewMembership()),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(),
            storyShareController = es.schsebastian.foodrats.core.data.share.RecordingStoryShareController(),
        )
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(listOf("Egg"), s.meal?.ingredients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun second_comment_batch_updates_state_does_not_freeze() = runTest {
        // Regression: the old code started a nested terminal `collect` inside the
        // outer comment collector, which suspended the outer collector forever after
        // the first batch — so a SECOND batch never reached state. flatMapLatest fixes it.
        val (vm, ports) = newSut()
        ports.accountPort.set(accountId("user-1"), Account(accountId("user-1"), "h", "Sebas", null, null))
        ports.accountPort.set(accountId("user-2"), Account(accountId("user-2"), "h", "Lia", null, null))
        ports.commentPort.emit(listOf(commentFrom("user-1", "first")))
        vm.state.test {
            assertEquals(
                listOf("first"),
                expectMostRecentItem().commentRows.map { it.text },
            )

            // Second batch must drive state too — the frozen old code never delivered this.
            ports.commentPort.emit(listOf(
                commentFrom("user-1", "first"),
                commentFrom("user-2", "second"),
            ))
            assertEquals(
                listOf("first", "second"),
                awaitItem().commentRows.map { it.text },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun surfaces_comment_read_error_when_port_fails() = runTest {
        val (vm, ports) = newSut()
        ports.commentPort.emitError(CommentError.Read.Unavailable)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(CommentError.Read.Unavailable, s.commentReadError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun comment_deletability_updates_when_crew_owner_changes() = runTest {
        // Regression (feed-02): owner was snapshotted once at subscription, so a crew-owner
        // handover never re-derived comment-row deletability. With owner derived reactively,
        // a row authored by someone else becomes deletable the moment the viewer becomes owner.
        val commentPort = FakeMealCommentPort()
        val accountPort = FakeAccountReadPort()
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val ownerPort = MutableCrewOwnerPort(initial = null)
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        val vm = MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, FakeMealReadPort(), session, es.schsebastian.foodrats.feature.feed.domain.usecase.FakeBlockedAccountsPort()),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = accountPort,
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = FakeCrewBlindVotingPort(),
            session = session,
            clock = FixedClock(),
            zone = zone,
            deleteMeal = DeleteMealUseCase(FakeMealDeletePort()),
            deleteMyMeal = DeleteMyMealUseCase(FakeMealDeletePort(), FakeCrewMembership()),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = ownerPort,
            storyShareController = es.schsebastian.foodrats.core.data.share.RecordingStoryShareController(),
        )
        commentPort.emit(listOf(commentFrom("u-other", "hi")))
        accountPort.set(accountId("u-other"), Account(accountId("u-other"), "h", "Other", null, null))
        vm.state.test {
            // Viewer is neither author nor owner yet -> not deletable.
            assertFalse(expectMostRecentItem().commentRows.single().canDelete)
            // Viewer becomes the crew owner -> the SAME row must now be deletable.
            ownerPort.owner.value = viewerId
            assertTrue(awaitItem().commentRows.single().canDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
