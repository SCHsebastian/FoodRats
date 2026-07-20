package es.schsebastian.foodrats.feature.stats.presentation.calendar

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveMyMealCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull

private class FakeSession(session: Session?) : SessionProvider {
    val s = MutableStateFlow(session)
    override val current: Flow<Session?> = s
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        s.value?.let { Result.success(it) } ?: Result.failure(SessionError.NotSignedIn)
}

private class FakeActive(initial: CrewId?) : ActiveCrewProvider {
    val s = MutableStateFlow(initial)
    override val current = s
    override suspend fun set(crewId: CrewId) { s.value = crewId }
    override suspend fun clear() { s.value = null }
}

private class FakeRead(initial: List<MealWithRatings>) : MealReadPort {
    val flow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(Result.success(initial))
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = flow
}

@OptIn(ExperimentalCoroutinesApi::class)
class MealCalendarViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-05-21T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crew = (CrewId.of("c") as Result.Ok).value

    private fun makeViewModel(): MealCalendarViewModel {
        val useCase = ObserveMyMealCalendarUseCase(
            FakeActive(crew),
            FakeSession(Session(me, null)),
            FakeRead(emptyList()),
            zone,
        )
        return MealCalendarViewModel(useCase, clock, zone)
    }

    @Test fun init_seeds_current_month_and_today() = runTest {
        val vm = makeViewModel()
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(LocalDate(2026, 5, 1), s.month)
            assertEquals(LocalDate(2026, 5, 21), s.today)
            assertNull(s.selectedDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun previous_month_moves_cursor_back() = runTest {
        val vm = makeViewModel()
        vm.onIntent(MealCalendarIntent.PreviousMonth)
        vm.state.test {
            assertEquals(LocalDate(2026, 4, 1), expectMostRecentItem().month)
            cancelAndIgnoreRemainingEvents()
        }
        vm.onIntent(MealCalendarIntent.PreviousMonth)
        vm.state.test {
            assertEquals(LocalDate(2026, 3, 1), expectMostRecentItem().month)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun next_month_is_clamped_at_current_month() = runTest {
        val vm = makeViewModel()
        // Already at the current month → NextMonth is a no-op.
        vm.onIntent(MealCalendarIntent.NextMonth)
        vm.state.test {
            assertEquals(LocalDate(2026, 5, 1), expectMostRecentItem().month)
            cancelAndIgnoreRemainingEvents()
        }
        // Back one, forward two → still clamped at the current month.
        vm.onIntent(MealCalendarIntent.PreviousMonth)
        vm.onIntent(MealCalendarIntent.NextMonth)
        vm.onIntent(MealCalendarIntent.NextMonth)
        vm.state.test {
            assertEquals(LocalDate(2026, 5, 1), expectMostRecentItem().month)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun day_selected_updates_selection_and_toggles_off() = runTest {
        val vm = makeViewModel()
        val day = LocalDate(2026, 5, 5)
        vm.onIntent(MealCalendarIntent.DaySelected(day))
        vm.state.test {
            assertEquals(day, expectMostRecentItem().selectedDay)
            cancelAndIgnoreRemainingEvents()
        }
        // Tapping the same day again deselects.
        vm.onIntent(MealCalendarIntent.DaySelected(day))
        vm.state.test {
            assertNull(expectMostRecentItem().selectedDay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun month_change_resets_selection() = runTest {
        val vm = makeViewModel()
        vm.onIntent(MealCalendarIntent.DaySelected(LocalDate(2026, 5, 5)))
        vm.onIntent(MealCalendarIntent.PreviousMonth)
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(LocalDate(2026, 4, 1), s.month)
            assertNull(s.selectedDay)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
