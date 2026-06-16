package es.schsebastian.foodrats.feature.meal.presentation.nudge

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureNudgeViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val clock = object : Clock { override fun now() = Instant.parse("2026-05-16T12:00:00Z") }

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val me = (AccountId.of("me") as Result.Ok).value
    private val other = (AccountId.of("other") as Result.Ok).value

    private fun mealBy(accountId: AccountId): MealWithRatings = MealWithRatings(
        meal = Meal(
            id = (MealId.of("m-1") as Result.Ok).value,
            author = MealAuthor(accountId, "Sam", null),
            crewId = crew,
            day = MealDay(today, zone),
            slot = MealSlot.Lunch,
            photoUrl = "https://example/p.jpg",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        ),
        ratings = emptyList(),
    )

    private class FakeSessionProvider(session: Session?) : SessionProvider {
        override val current: Flow<Session?> = MutableStateFlow(session)
        override suspend fun requireCurrent(): Result<Session, SessionError> = Result.failure(SessionError.NotSignedIn)
    }

    private class FakeActiveCrewProvider(initial: CrewId?) : ActiveCrewProvider {
        private val state = MutableStateFlow(initial)
        override val current: Flow<CrewId?> = state
        override suspend fun set(crewId: CrewId) { state.value = crewId }
        override suspend fun clear() { state.value = null }
    }

    private class FakeMealReadPort(
        private val feed: List<MealWithRatings>,
        private val readError: MealReadError? = null,
    ) : MealReadPort {
        override fun observeFeed(crewId: CrewId, day: MealDay): Flow<Result<List<MealWithRatings>, MealReadError>> =
            MutableStateFlow(readError?.let { Result.failure(it) } ?: Result.success(feed))
        override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = observeFeed(crewId, from)
    }

    private fun viewModel(
        feed: List<MealWithRatings> = emptyList(),
        readError: MealReadError? = null,
        accountId: AccountId? = me,
        crewId: CrewId? = crew,
    ) = CaptureNudgeViewModel(
        mealRead = FakeMealReadPort(feed, readError),
        activeCrew = FakeActiveCrewProvider(crewId),
        session = FakeSessionProvider(accountId?.let { Session(it, crewId) }),
        clock = clock,
        zone = zone,
    )

    @Test fun own_meal_in_feed_marks_posted_today() = runTest {
        viewModel(feed = listOf(mealBy(me))).state.test {
            assertTrue(expectMostRecentItem().hasPostedToday)
        }
    }

    @Test fun only_others_meals_means_not_posted_today() = runTest {
        viewModel(feed = listOf(mealBy(other))).state.test {
            assertFalse(expectMostRecentItem().hasPostedToday)
        }
    }

    @Test fun no_account_reports_posted_today_no_nag() = runTest {
        viewModel(feed = listOf(mealBy(other)), accountId = null).state.test {
            assertTrue(expectMostRecentItem().hasPostedToday)
        }
    }

    @Test fun no_active_crew_reports_posted_today_no_nag() = runTest {
        viewModel(feed = listOf(mealBy(other)), crewId = null).state.test {
            assertTrue(expectMostRecentItem().hasPostedToday)
        }
    }

    @Test fun read_error_reports_posted_today_no_nag() = runTest {
        viewModel(readError = MealReadError.Unavailable).state.test {
            assertTrue(expectMostRecentItem().hasPostedToday)
        }
    }
}
