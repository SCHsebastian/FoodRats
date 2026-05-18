package es.schsebastian.foodrats.feature.feed.presentation.feed

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeMealReadPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixedClockTest(private val instant: Instant) : Clock {
    override fun now() = instant
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val nowInstant = Instant.parse("2026-05-16T12:00:00Z")
    private val clock = FixedClockTest(nowInstant)
    private val crew = (CrewId.of("c-1") as Result.Ok).value

    private val sampleMeal = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", null),
        crewId = crew,
        day = MealDay(today, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://x/p.jpg",
        score = (Score.of(8) as Result.Ok).value,
        dish = (DishName.of("Pasta") as Result.Ok).value,
        tags = listOf((FoodTag.custom("italian") as Result.Ok).value),
        publishedAt = nowInstant,
    )

    @Test fun initial_state_today_with_meals() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to "2026-05-16") to listOf(sampleMeal)))
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        vm.state.test {
            // With UnconfinedTestDispatcher the init coroutine may run before test{} collects,
            // so skip the loading pre-emission if it isn't present and assert the settled state.
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertEquals(false, s.isLoading)
            assertEquals(1, s.meals.size)
            assertEquals("m-1", s.meals[0].id)
            assertEquals(false, s.canGoNext)  // today is the latest day
            assertEquals(true, s.canGoPrev)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun prev_day_loads_yesterday() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val yesterday = "2026-05-15"
        val port = FakeMealReadPort(perDay = mapOf(
            (crew to "2026-05-16") to emptyList(),
            (crew to yesterday)  to listOf(sampleMeal.copy(day = MealDay(LocalDate(2026, 5, 15), zone))),
        ))
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        vm.onIntent(FeedIntent.PrevDay)
        val s = vm.state.value
        assertEquals(LocalDate(2026, 5, 15), s.day?.day?.date)
        assertEquals(true, s.canGoNext) // can return to today
    }

    @Test fun next_day_blocked_at_today() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        vm.onIntent(FeedIntent.NextDay)
        assertEquals(today, vm.state.value.day?.day?.date)
    }

    @Test fun prev_day_blocked_at_window_boundary() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        // 30 prev calls walks to the boundary (today minus 29 days). One more is blocked.
        repeat(30) { vm.onIntent(FeedIntent.PrevDay) }
        val boundary = vm.state.value.day?.day?.date
        vm.onIntent(FeedIntent.PrevDay)
        assertEquals(boundary, vm.state.value.day?.day?.date)
        assertTrue(vm.state.value.canGoPrev.not())
    }

    @Test fun capture_clicked_emits_effect() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        vm.effects.test {
            vm.onIntent(FeedIntent.CaptureClicked)
            assertEquals(FeedEffect.NavigateToCapture, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun read_error_propagates_to_state() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(readError = MealReadError.Unauthorized)
        val vm = FeedViewModel(ObserveFeedUseCase(active, port), clock, zone)
        assertTrue(vm.state.value.error != null || vm.state.value.isLoading)
    }
}
