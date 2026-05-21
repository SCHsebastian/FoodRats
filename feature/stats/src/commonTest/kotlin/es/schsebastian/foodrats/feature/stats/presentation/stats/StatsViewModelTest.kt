package es.schsebastian.foodrats.feature.stats.presentation.stats

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
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 21)
    private val now = Instant.parse("2026-05-21T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crew = (CrewId.of("c") as Result.Ok).value

    private fun makeMealMine(): MealWithRatings {
        val meal = Meal(
            (MealId.of("m") as Result.Ok).value,
            MealAuthor(me, "Me", null),
            crew,
            MealDay(today, zone),
            MealSlot.Lunch,
            "u",
            (DishName.of("Pasta") as Result.Ok).value,
            Description.EMPTY,
            now,
        )
        return MealWithRatings(meal, emptyList())
    }

    private fun makeVm(): StatsViewModel {
        val mealsFlow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(
            Result.success(listOf(makeMealMine())),
        )
        val active = object : ActiveCrewProvider {
            override val current: Flow<CrewId?> = MutableStateFlow(crew)
            override suspend fun set(crewId: CrewId) {}
            override suspend fun clear() {}
        }
        val session = object : SessionProvider {
            override val current: Flow<Session?> = MutableStateFlow(Session(me, null))
            override suspend fun requireCurrent(): Result<Session, SessionError> =
                Result.success(Session(me, null))
        }
        val read = object : MealReadPort {
            override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
            override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = mealsFlow
        }
        return StatsViewModel(ObserveStatsUseCase(active, session, read, clock, zone))
    }

    @Test fun initial_state_loads_snapshot_for_week_tab() = runTest {
        val vm = makeVm()
        vm.state.test {
            val s = expectMostRecentItem()
            assertNotNull(s.snapshot)
            assertEquals(Tab.Week, s.selectedTab)
            assertEquals(1, s.snapshot!!.hero.personalStreak.days)
            assertEquals(1, s.snapshot!!.week.totalMeals)
            assertNull(s.snapshot!!.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun selecting_historic_tab_loads_historic_window() = runTest {
        val vm = makeVm()
        vm.onIntent(StatsIntent.SelectTab(Tab.Historic))
        vm.state.test {
            val s = expectMostRecentItem()
            assertEquals(Tab.Historic, s.selectedTab)
            assertNotNull(s.snapshot)
            assertNotNull(s.snapshot!!.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun refresh_increments_epoch() = runTest {
        val vm = makeVm()
        val initial = vm.state.value.epoch
        vm.onIntent(StatsIntent.Refresh)
        assertEquals(initial + 1, vm.state.value.epoch)
    }
}
