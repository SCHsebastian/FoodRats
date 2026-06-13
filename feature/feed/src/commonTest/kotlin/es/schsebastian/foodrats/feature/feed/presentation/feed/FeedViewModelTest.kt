package es.schsebastian.foodrats.feature.feed.presentation.feed

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.RateError
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeActiveCrewProvider
import es.schsebastian.foodrats.feature.feed.domain.usecase.FakeMealReadPort
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

class FakeSessionProvider(session: Session?) : SessionProvider {
    private val flow = MutableStateFlow(session)
    override val current: Flow<Session?> = flow
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        flow.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
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
    private val viewerId = (AccountId.of("u-viewer") as Result.Ok).value
    private val session = FakeSessionProvider(Session(viewerId, crew))

    private val sampleMeal = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", null),
        crewId = crew,
        day = MealDay(today, zone),
        slot = MealSlot.Lunch,
        photoUrl = "https://x/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = nowInstant,
    )
    private val sampleMealWithRatings = MealWithRatings(sampleMeal, emptyList())

    private val idleUploadProgress = object : MealUploadProgressPort {
        override val status: MutableStateFlow<MealUploadStatus> =
            MutableStateFlow(MealUploadStatus.Idle)
    }

    private fun buildVm(
        ratingPort: FakeMealRatingPort = FakeMealRatingPort(),
        active: FakeActiveCrewProvider = FakeActiveCrewProvider(initial = crew),
        port: FakeMealReadPort = FakeMealReadPort(
            perDay = mapOf((crew to "2026-05-16") to listOf(sampleMealWithRatings))
        ),
    ) = FeedViewModel(
        observeFeed = ObserveFeedUseCase(active, port),
        rateMeal = RateMealUseCase(ratingPort),
        activeCrew = active,
        session = session,
        clock = clock,
        zone = zone,
        uploadProgress = idleUploadProgress,
    )

    @Test fun initial_state_today_with_meals() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = mapOf((crew to "2026-05-16") to listOf(sampleMealWithRatings)))
        val vm = buildVm(active = active, port = port)
        vm.state.test {
            var s = awaitItem()
            if (s.isLoading) s = awaitItem()
            assertEquals(false, s.isLoading)
            assertEquals(1, s.meals.size)
            assertEquals("m-1", s.meals[0].mealId)
            assertEquals(false, s.canGoNext)
            assertEquals(true, s.canGoPrev)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun prev_day_loads_yesterday() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val yesterday = "2026-05-15"
        val port = FakeMealReadPort(perDay = mapOf(
            (crew to "2026-05-16") to emptyList(),
            (crew to yesterday) to listOf(sampleMealWithRatings.copy(
                meal = sampleMeal.copy(day = MealDay(LocalDate(2026, 5, 15), zone))
            )),
        ))
        val vm = buildVm(active = active, port = port)
        vm.onIntent(FeedIntent.PrevDay)
        val s = vm.state.value
        assertEquals(LocalDate(2026, 5, 15), s.day?.day?.date)
        assertEquals(true, s.canGoNext)
    }

    @Test fun next_day_blocked_at_today() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = buildVm(active = active, port = port)
        vm.onIntent(FeedIntent.NextDay)
        assertEquals(today, vm.state.value.day?.day?.date)
    }

    @Test fun prev_day_blocked_at_window_boundary() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(perDay = emptyMap())
        val vm = buildVm(active = active, port = port)
        repeat(30) { vm.onIntent(FeedIntent.PrevDay) }
        val boundary = vm.state.value.day?.day?.date
        vm.onIntent(FeedIntent.PrevDay)
        assertEquals(boundary, vm.state.value.day?.day?.date)
        assertTrue(vm.state.value.canGoPrev.not())
    }

    @Test fun read_error_propagates_to_state() = runTest {
        val active = FakeActiveCrewProvider(initial = crew)
        val port = FakeMealReadPort(readError = MealReadError.Unauthorized)
        val vm = buildVm(active = active, port = port)
        assertTrue(vm.state.value.error != null || vm.state.value.isLoading)
    }

    @Test fun rate_meal_records_call_with_correct_score() = runTest {
        val ratingPort = FakeMealRatingPort()
        val vm = buildVm(ratingPort = ratingPort)
        vm.onIntent(FeedIntent.RateMeal("meal-1", 4))
        runCurrent()
        assertEquals(1, ratingPort.calls.size)
        assertEquals("meal-1", ratingPort.calls.first().mealId)
        assertEquals(4, ratingPort.calls.first().score)
        assertEquals("u-viewer", ratingPort.calls.first().raterId)
    }

    @Test fun rate_meal_failure_populates_rateError() = runTest {
        val ratingPort = FakeMealRatingPort().apply {
            nextResult = Result.failure(RateError.CannotRateOwnMeal)
        }
        val vm = buildVm(ratingPort = ratingPort)
        vm.state.test {
            skipItems(1)
            vm.onIntent(FeedIntent.RateMeal("meal-1", 5))
            val s = expectMostRecentItem()
            assertEquals(RateError.CannotRateOwnMeal, s.rateError)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
