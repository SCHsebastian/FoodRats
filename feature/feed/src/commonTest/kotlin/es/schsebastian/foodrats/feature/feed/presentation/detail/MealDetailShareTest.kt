package es.schsebastian.foodrats.feature.feed.presentation.detail

import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.share.RecordingStoryShareController
import es.schsebastian.foodrats.core.data.share.StoryShareOutcome
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsValue
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** §12: the meal-detail share flow — launcher invoked, analytics fired only on success. */
@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailShareTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val crew = (CrewId.of("c-1") as Result.Ok).value
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val day = MealDay(LocalDate.parse("2026-05-20"), zone)
    private val clock = object : es.schsebastian.foodrats.core.domain.time.Clock {
        override fun now() = Instant.parse("2026-05-20T12:00:00Z")
    }

    private fun newSut(
        share: RecordingStoryShareController,
        analytics: RecordingAnalyticsTracker,
    ): MealDetailViewModel {
        val meal = Meal(
            id = (MealId.of("meal-1") as Result.Ok).value,
            author = MealAuthor((AccountId.of("u-author") as Result.Ok).value, "Author", null),
            crewId = crew,
            day = day,
            slot = MealSlot.Lunch,
            photoUrl = "https://signed/plate.jpg",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = Instant.parse("2026-05-20T10:00:00Z"),
        )
        val readPort = FakeMealReadPort(
            perDay = mapOf((crew to day.toKey()) to listOf(MealWithRatings(meal, emptyList()))),
        )
        val commentPort = FakeMealCommentPort()
        val active = FakeActiveCrewProvider(initial = crew)
        val session = FakeSessionProvider(Session(viewerId, crew))
        val connectivity = FakeConnectivityPort(online = true)
        val outbox = RecordingOutboxPort()
        return MealDetailViewModel(
            mealId = "meal-1",
            dayIso = "2026-05-20",
            observeFeed = ObserveFeedUseCase(active, readPort),
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
            crewOwner = FakeCrewOwnerPort(),
            storyShareController = share,
            analytics = analytics,
        )
    }

    @Test fun share_invokes_launcher_with_plate_url_and_fires_plate_event_on_success() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.OpenedInstagram)
        val analytics = RecordingAnalyticsTracker()
        val vm = newSut(share, analytics)
        vm.onIntent(MealDetailIntent.ShareTapped)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(false, s.isPreparingShare)
            assertEquals(ShareOutcomeUi.Succeeded, s.shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, share.callCount)
        assertEquals("https://signed/plate.jpg", share.lastCall!!.plateUrl)
        val event = analytics.events.filterIsInstance<AnalyticsEvent.PlateShared>().single()
        assertEquals("share", event.name)
        assertEquals("meal-1", (event.params["item_id"] as AnalyticsValue.Text).value)
        assertEquals("plate", (event.params["content_type"] as AnalyticsValue.Text).value)
    }

    @Test fun share_does_not_fire_event_on_failed_outcome() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.Failed)
        val analytics = RecordingAnalyticsTracker()
        val vm = newSut(share, analytics)
        vm.onIntent(MealDetailIntent.ShareTapped)
        vm.state.test {
            assertEquals(ShareOutcomeUi.Failed, expectMostRecentItem().shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, share.callCount)
        assertEquals(0, analytics.events.filterIsInstance<AnalyticsEvent.PlateShared>().size)
    }

    @Test fun fallback_sheet_outcome_still_fires_event_and_shows_sheet_toast() = runTest {
        val share = RecordingStoryShareController(outcome = StoryShareOutcome.OpenedFallbackSheet)
        val analytics = RecordingAnalyticsTracker()
        val vm = newSut(share, analytics)
        vm.onIntent(MealDetailIntent.ShareTapped)
        vm.state.test {
            assertEquals(ShareOutcomeUi.OpenedSheet, expectMostRecentItem().shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, analytics.events.filterIsInstance<AnalyticsEvent.PlateShared>().size)
    }

    @Test fun dismiss_share_outcome_clears_toast() = runTest {
        val vm = newSut(RecordingStoryShareController(), RecordingAnalyticsTracker())
        vm.onIntent(MealDetailIntent.ShareTapped)
        vm.onIntent(MealDetailIntent.DismissShareOutcome)
        vm.state.test {
            assertNull(expectMostRecentItem().shareOutcome)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
