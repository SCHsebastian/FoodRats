package es.schsebastian.foodrats.feature.stats.presentation.stats

import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadError
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.Session
import es.schsebastian.foodrats.core.domain.session.SessionError
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val now = Instant.parse("2026-05-16T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crew = (CrewId.of("c") as Result.Ok).value

    @Test fun emits_snapshot_after_meal_stream() = runTest {
        val mealMine = Meal(
            (MealId.of("m") as Result.Ok).value,
            MealAuthor(me, "Me", null),
            crew,
            MealDay(today, zone),
            "u",
            (Score.of(7) as Result.Ok).value,
            (DishName.of("Pasta") as Result.Ok).value,
            emptyList(),
            now,
        )
        val active = object : ActiveCrewProvider {
            override val current: Flow<CrewId?> = MutableStateFlow(crew)
            override suspend fun set(crewId: CrewId) {}
            override suspend fun clear() {}
        }
        val session = object : SessionProvider {
            override val current: Flow<Session?> = MutableStateFlow(Session(me, null))
            override suspend fun requireCurrent(): Result<Session, SessionError> = Result.success(Session(me, null))
        }
        val read = object : MealReadPort {
            override fun observeFeed(crewId: CrewId, day: MealDay) =
                MutableStateFlow(Unit).map<Unit, Result<List<Meal>, MealReadError>> { Result.success(listOf(mealMine)) }
            override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = observeFeed(crewId, from)
        }
        val vm = StatsViewModel(ObserveStatsUseCase(active, session, read, clock, zone))
        val s = vm.state.value
        assertNotNull(s.snapshot)
        assertEquals(1, s.snapshot!!.personalStreak.days)
    }
}
