package es.schsebastian.foodrats.feature.stats.domain.usecase

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
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeSession(val session: Session) : SessionProvider {
    override val current: Flow<Session?> = MutableStateFlow(session)
    override suspend fun requireCurrent(): Result<Session, SessionError> = Result.success(session)
}

class FakeActive(initial: CrewId?) : ActiveCrewProvider {
    val s = MutableStateFlow(initial)
    override val current = s
    override suspend fun set(crewId: CrewId) { s.value = crewId }
    override suspend fun clear() { s.value = null }
}

class FakeRead(val aggregates: List<MealWithRatings>, val err: MealReadError? = null) : MealReadPort {
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused in this test")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) =
        MutableStateFlow(Unit).map<Unit, Result<List<MealWithRatings>, MealReadError>> {
            if (err != null) Result.failure(err) else Result.success(aggregates)
        }
}

class FixedClock(val instant: Instant) : Clock { override fun now() = instant }

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveStatsUseCaseTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val now = Instant.parse("2026-05-16T12:00:00Z")
    private val clock = FixedClock(now)
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crew = (CrewId.of("c-1") as Result.Ok).value

    @Test fun no_active_crew_emits_NoActiveCrew() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(null), FakeSession(Session(me, null)), FakeRead(emptyList()), clock, zone)
        uc().test {
            assertEquals(Result.failure(StatsError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun ok_path_emits_snapshot() = runTest {
        val mealMine = Meal(
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
        val uc = ObserveStatsUseCase(
            FakeActive(crew),
            FakeSession(Session(me, null)),
            FakeRead(listOf(MealWithRatings(mealMine, emptyList()))),
            clock,
            zone,
        )
        uc().test {
            val r = awaitItem()
            assertIs<Result.Ok<es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot>>(r)
            assertEquals(1, r.value.personalStreak.days)
            assertEquals(1, r.value.crewStreak.days)
            assertEquals(1, r.value.topDishes.size)
            // meal has no ratings → leaderboard entry is excluded
            assertEquals(0, r.value.leaderboard.entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun read_error_propagates() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(crew), FakeSession(Session(me, null)), FakeRead(emptyList(), MealReadError.Unauthorized), clock, zone)
        uc().test {
            assertEquals(Result.failure(StatsError.Read.Unauthorized), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Regression: meals come back from Firestore with TimeZone.UTC (MealMapper hardcodes it)
    // even when the reader's session zone is non-UTC. dayKey is the source of truth, so the
    // streak must still count when zones disagree.
    @Test fun streak_counts_when_meal_zone_differs_from_session_zone() = runTest {
        val madrid = TimeZone.of("Europe/Madrid")
        val mealMine = Meal(
            (MealId.of("m") as Result.Ok).value,
            MealAuthor(me, "Me", null),
            crew,
            MealDay(today, TimeZone.UTC),  // simulates production data
            MealSlot.Lunch,
            "u",
            (DishName.of("Pasta") as Result.Ok).value,
            Description.EMPTY,
            now,
        )
        val uc = ObserveStatsUseCase(
            FakeActive(crew),
            FakeSession(Session(me, null)),
            FakeRead(listOf(MealWithRatings(mealMine, emptyList()))),
            clock,
            madrid,
        )
        uc().test {
            val r = awaitItem()
            assertIs<Result.Ok<es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot>>(r)
            assertEquals(1, r.value.personalStreak.days)
            assertEquals(1, r.value.crewStreak.days)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
