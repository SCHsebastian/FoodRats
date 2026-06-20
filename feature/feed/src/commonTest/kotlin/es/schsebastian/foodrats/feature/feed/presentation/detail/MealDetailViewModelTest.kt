package es.schsebastian.foodrats.feature.feed.presentation.detail

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealComment
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealDeleteError
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
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
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Permission gating (delete-meal / delete-comment) + the author-vs-owner delete-routing split
 * + invalid-day handling. Identity-join and share flows are covered separately by
 * [MealDetailCommentIdentityTest] / [MealDetailShareTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val authorId = (AccountId.of("u-author") as Result.Ok).value
    private val day = MealDay(LocalDate.parse("2026-05-20"), zone)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-05-20T12:00:00Z")
    }

    /** Records which delete path the VM took (owner moderation vs. author fan-out). */
    private class RecordingMealDeletePort : MealDeletePort {
        var deleteCalls = 0
        var deleteFromAllCrewsCalls = 0
        override suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealDeleteError> {
            deleteCalls++
            return Result.success(Unit)
        }
        override suspend fun deleteFromAllCrews(
            crewIds: Set<CrewId>,
            authorId: AccountId,
            day: MealDay,
            slot: MealSlot,
        ): Result<Unit, MealDeleteError> {
            deleteFromAllCrewsCalls++
            return Result.success(Unit)
        }
    }

    private fun mealBy(author: AccountId) = Meal(
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

    private fun newSut(
        mealAuthor: AccountId = authorId,
        owner: AccountId? = null,
        dayIso: String = "2026-05-20",
        commentPort: FakeMealCommentPort = FakeMealCommentPort(),
        deletePort: MealDeletePort = FakeMealDeletePort(),
        deleteMyMealPort: MealDeletePort = FakeMealDeletePort(),
        blindVoting: FakeCrewBlindVotingPort = FakeCrewBlindVotingPort(),
    ): MealDetailViewModel {
        val readPort = FakeMealReadPort(
            perDay = mapOf((crew to day.toKey()) to listOf(MealWithRatings(mealBy(mealAuthor), emptyList()))),
        )
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        return MealDetailViewModel(
            mealId = "meal-1",
            dayIso = dayIso,
            observeFeed = ObserveFeedUseCase(active, readPort),
            rateMeal = RateMealUseCase(FakeMealRatingPort(), connectivity, outbox, RecordingOptimisticMealWritePort()),
            commentPort = commentPort,
            connectivity = connectivity,
            outbox = outbox,
            accountReadPort = FakeAccountReadPort(),
            ingredientRead = FakeIngredientReadPort(),
            activeCrew = active,
            blindVoting = blindVoting,
            session = session,
            clock = clock,
            zone = zone,
            deleteMeal = DeleteMealUseCase(deletePort),
            deleteMyMeal = DeleteMyMealUseCase(
                deleteMyMealPort,
                FakeCrewMembership(listOf(CrewSummary(crew, "Crew"))),
            ),
            deleteComment = DeleteCommentUseCase(commentPort, connectivity, outbox),
            crewOwner = FakeCrewOwnerPort(owner),
            storyShareController = RecordingStoryShareController(),
        )
    }

    // --- canDeleteMeal gate -----------------------------------------------------

    @Test fun can_delete_meal_when_viewer_is_author() = runTest {
        val vm = newSut(mealAuthor = viewerId, owner = null)
        vm.state.test {
            assertTrue(expectMostRecentItem().canDeleteMeal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun can_delete_meal_when_viewer_is_crew_owner() = runTest {
        val vm = newSut(mealAuthor = authorId, owner = viewerId)
        vm.state.test {
            assertTrue(expectMostRecentItem().canDeleteMeal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cannot_delete_meal_when_viewer_is_neither_author_nor_owner() = runTest {
        val vm = newSut(mealAuthor = authorId, owner = authorId)
        vm.state.test {
            assertFalse(expectMostRecentItem().canDeleteMeal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- blind-voting author masking (regression: detail leaked the cook) -------

    @Test fun author_masked_in_detail_when_blind_voting_on_and_viewer_has_not_voted() = runTest {
        val vm = newSut(mealAuthor = authorId, blindVoting = FakeCrewBlindVotingPort(blindVoting = true))
        vm.state.test {
            assertTrue(expectMostRecentItem().meal!!.authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun author_not_masked_in_detail_when_blind_voting_off() = runTest {
        val vm = newSut(mealAuthor = authorId, blindVoting = FakeCrewBlindVotingPort(blindVoting = false))
        vm.state.test {
            assertFalse(expectMostRecentItem().meal!!.authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun own_meal_never_masked_in_detail_even_with_blind_voting_on() = runTest {
        val vm = newSut(mealAuthor = viewerId, blindVoting = FakeCrewBlindVotingPort(blindVoting = true))
        vm.state.test {
            assertFalse(expectMostRecentItem().meal!!.authorMasked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- comment-row canDelete gate --------------------------------------------

    private fun commentFrom(author: AccountId): MealComment = MealComment(
        id = MealCommentId("cmt-1"),
        mealId = (MealId.of("meal-1") as Result.Ok).value,
        crewId = crew,
        authorId = author,
        text = (CommentText.of("hi") as Result.Ok).value,
        createdAt = Instant.parse("2026-05-20T11:59:00Z"),
    )

    @Test fun comment_row_deletable_when_viewer_is_comment_author() = runTest {
        val commentPort = FakeMealCommentPort()
        val vm = newSut(mealAuthor = authorId, owner = null, commentPort = commentPort)
        commentPort.emit(listOf(commentFrom(viewerId)))
        vm.state.test {
            assertTrue(expectMostRecentItem().commentRows.single().canDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun comment_row_deletable_when_viewer_is_crew_owner() = runTest {
        val other = (AccountId.of("u-other") as Result.Ok).value
        val commentPort = FakeMealCommentPort()
        val vm = newSut(mealAuthor = authorId, owner = viewerId, commentPort = commentPort)
        commentPort.emit(listOf(commentFrom(other)))
        vm.state.test {
            assertTrue(expectMostRecentItem().commentRows.single().canDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun comment_row_not_deletable_when_viewer_is_neither() = runTest {
        val other = (AccountId.of("u-other") as Result.Ok).value
        val commentPort = FakeMealCommentPort()
        val vm = newSut(mealAuthor = authorId, owner = authorId, commentPort = commentPort)
        commentPort.emit(listOf(commentFrom(other)))
        vm.state.test {
            assertFalse(expectMostRecentItem().commentRows.single().canDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- author-vs-owner delete routing ----------------------------------------

    @Test fun delete_as_author_fans_out_to_all_crews() = runTest {
        val ownerPath = RecordingMealDeletePort()
        val authorPath = RecordingMealDeletePort()
        val vm = newSut(
            mealAuthor = viewerId, // viewer authored the meal
            owner = null,
            deletePort = ownerPath,
            deleteMyMealPort = authorPath,
        )
        vm.onIntent(MealDetailIntent.DeleteMeal)
        runCurrent()
        assertEquals(1, authorPath.deleteFromAllCrewsCalls)
        assertEquals(0, ownerPath.deleteCalls)
        vm.state.test {
            assertTrue(expectMostRecentItem().mealDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun delete_as_owner_only_removes_the_crew_in_view() = runTest {
        val ownerPath = RecordingMealDeletePort()
        val authorPath = RecordingMealDeletePort()
        val vm = newSut(
            mealAuthor = authorId, // someone else authored it
            owner = viewerId,      // viewer is the crew owner moderating
            deletePort = ownerPath,
            deleteMyMealPort = authorPath,
        )
        vm.onIntent(MealDetailIntent.DeleteMeal)
        runCurrent()
        assertEquals(1, ownerPath.deleteCalls)
        assertEquals(0, authorPath.deleteFromAllCrewsCalls)
        vm.state.test {
            assertTrue(expectMostRecentItem().mealDeleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- invalid day -----------------------------------------------------------

    @Test fun invalid_day_iso_sets_not_found_and_stops_loading() = runTest {
        val vm = newSut(dayIso = "not-a-date")
        vm.state.test {
            val s = expectMostRecentItem()
            assertTrue(s.notFound)
            assertFalse(s.isLoading)
            assertFalse(s.commentsLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
