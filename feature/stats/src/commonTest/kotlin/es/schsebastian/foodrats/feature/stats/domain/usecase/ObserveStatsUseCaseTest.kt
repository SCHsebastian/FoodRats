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
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeSession(val session: Session?) : SessionProvider {
    val s = MutableStateFlow(session)
    override val current: Flow<Session?> = s
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session) else Result.failure(SessionError.NotSignedIn)
}

private class FakeActive(initial: CrewId?) : ActiveCrewProvider {
    val s = MutableStateFlow(initial)
    override val current = s
    override suspend fun set(crewId: CrewId) { s.value = crewId }
    override suspend fun clear() { s.value = null }
}

private class FakeRead(initial: List<MealWithRatings>, val err: MealReadError? = null) : MealReadPort {
    val flow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(
        if (err != null) Result.failure(err) else Result.success(initial),
    )
    fun update(value: List<MealWithRatings>) { flow.value = Result.success(value) }
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = flow
}

private class FixedClock(val instant: Instant) : Clock { override fun now() = instant }

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveStatsUseCaseTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 21)
    private val now = Instant.parse("2026-05-21T12:00:00Z")
    private val clock = FixedClock(now)
    private val me = (AccountId.of("me") as Result.Ok).value
    private val crewId = (CrewId.of("c-1") as Result.Ok).value

    private fun makeMeal(authorId: AccountId, date: LocalDate): MealWithRatings {
        val meal = Meal(
            id = (MealId.of("m-${authorId.value}-${date}") as Result.Ok).value,
            author = MealAuthor(authorId, authorId.value, null),
            crewId = crewId,
            day = MealDay(date, zone),
            slot = MealSlot.Lunch,
            photoUrl = "u",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = now,
        )
        return MealWithRatings(meal, emptyList())
    }

    @Test fun emits_NotSignedIn_when_no_session() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(crewId), FakeSession(null), FakeRead(emptyList()), clock, zone)
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Session.NotSignedIn), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emits_NoActiveCrew_when_no_crew() = runTest {
        val uc = ObserveStatsUseCase(FakeActive(null), FakeSession(Session(me, null)), FakeRead(emptyList()), clock, zone)
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun ok_path_populates_week_and_month_hero_no_historic() = runTest {
        val mine = makeMeal(me, today)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            val r = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r)
            assertEquals(1, r.value.hero.personalStreak.days)
            assertEquals(1, r.value.hero.platesToday)
            assertEquals(1, r.value.week.totalMeals)
            assertEquals(1, r.value.month.totalMeals)
            assertNull(r.value.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun historic_populates_when_enabled_flips_true() = runTest {
        val mine = makeMeal(me, today)
        val historicFlag = MutableStateFlow(false)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            clock,
            zone,
        )
        uc(historicFlag, flowOf(0)).test {
            // first emission: historic null
            val r1 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r1)
            assertNull(r1.value.historic)
            // flip flag → historic populated
            historicFlag.value = true
            val r2 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r2)
            assertNotNull(r2.value.historic)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun epoch_increment_re_emits() = runTest {
        val mine = makeMeal(me, today)
        val epochFlow = MutableStateFlow(0)
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(listOf(mine)),
            clock,
            zone,
        )
        uc(flowOf(false), epochFlow).test {
            val r1 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r1)
            epochFlow.value = 1
            val r2 = awaitItem()
            assertIs<Result.Ok<StatsSnapshot>>(r2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun current_error_propagates() = runTest {
        val uc = ObserveStatsUseCase(
            FakeActive(crewId),
            FakeSession(Session(me, null)),
            FakeRead(emptyList(), MealReadError.Unauthorized),
            clock,
            zone,
        )
        uc(flowOf(false), flowOf(0)).test {
            assertEquals(Result.failure(StatsError.Read.Unauthorized), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
