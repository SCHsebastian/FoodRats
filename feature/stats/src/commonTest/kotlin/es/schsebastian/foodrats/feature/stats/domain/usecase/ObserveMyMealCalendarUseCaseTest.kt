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
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.MealCalendarMonth
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

private class CalendarFakeSession(val session: Session?) : SessionProvider {
    val s = MutableStateFlow(session)
    override val current: Flow<Session?> = s
    override suspend fun requireCurrent(): Result<Session, SessionError> =
        if (session != null) Result.success(session) else Result.failure(SessionError.NotSignedIn)
}

private class CalendarFakeActive(initial: CrewId?) : ActiveCrewProvider {
    val s = MutableStateFlow(initial)
    override val current = s
    override suspend fun set(crewId: CrewId) { s.value = crewId }
    override suspend fun clear() { s.value = null }
}

private class CalendarFakeRead(initial: List<MealWithRatings>, err: MealReadError? = null) : MealReadPort {
    val flow = MutableStateFlow<Result<List<MealWithRatings>, MealReadError>>(
        if (err != null) Result.failure(err) else Result.success(initial),
    )
    override fun observeFeed(crewId: CrewId, day: MealDay) = error("unused")
    override fun observeRange(crewId: CrewId, from: MealDay, to: MealDay) = flow
}

class ObserveMyMealCalendarUseCaseTest {

    private val zone = TimeZone.UTC
    private val month = LocalDate(2026, 5, 1)
    private val me = (AccountId.of("me") as Result.Ok).value
    private val other = (AccountId.of("other") as Result.Ok).value
    private val crewId = (CrewId.of("c-1") as Result.Ok).value

    private fun makeMeal(
        authorId: AccountId,
        date: LocalDate,
        id: String = "m-${authorId.value}-$date",
        publishedAt: Instant = Instant.parse("2026-05-21T12:00:00Z"),
    ): MealWithRatings {
        val meal = Meal(
            id = (MealId.of(id) as Result.Ok).value,
            author = MealAuthor(authorId, authorId.value, null),
            crewId = crewId,
            day = MealDay(date, zone),
            slot = MealSlot.Lunch,
            photoUrl = "u",
            dish = (DishName.of("Pasta") as Result.Ok).value,
            description = Description.EMPTY,
            publishedAt = publishedAt,
        )
        return MealWithRatings(meal, emptyList())
    }

    @Test fun groups_own_meals_by_day_and_excludes_other_authors() = runTest {
        val day5 = LocalDate(2026, 5, 5)
        val day9 = LocalDate(2026, 5, 9)
        val lateMeal = makeMeal(me, day5, id = "late", publishedAt = Instant.parse("2026-05-05T20:00:00Z"))
        val earlyMeal = makeMeal(me, day5, id = "early", publishedAt = Instant.parse("2026-05-05T08:00:00Z"))
        val otherDayMeal = makeMeal(me, day9)
        val strangersMeal = makeMeal(other, day5)
        val uc = ObserveMyMealCalendarUseCase(
            CalendarFakeActive(crewId),
            CalendarFakeSession(Session(me, null)),
            CalendarFakeRead(listOf(lateMeal, earlyMeal, otherDayMeal, strangersMeal)),
            zone,
        )
        uc(flowOf(month)).test {
            val r = awaitItem()
            assertIs<Result.Ok<MealCalendarMonth>>(r)
            assertEquals(month, r.value.month)
            assertEquals(setOf(day5, day9), r.value.mealsByDay.keys)
            // Own meals only, each day ordered by publishedAt.
            assertEquals(listOf(earlyMeal, lateMeal), r.value.mealsByDay[day5])
            assertEquals(listOf(otherDayMeal), r.value.mealsByDay[day9])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emits_NotSignedIn_when_no_session() = runTest {
        val uc = ObserveMyMealCalendarUseCase(CalendarFakeActive(crewId), CalendarFakeSession(null), CalendarFakeRead(emptyList()), zone)
        uc(flowOf(month)).test {
            assertEquals(Result.failure(StatsError.Session.NotSignedIn), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun emits_NoActiveCrew_when_no_crew() = runTest {
        val uc = ObserveMyMealCalendarUseCase(CalendarFakeActive(null), CalendarFakeSession(Session(me, null)), CalendarFakeRead(emptyList()), zone)
        uc(flowOf(month)).test {
            assertEquals(Result.failure(StatsError.Session.NoActiveCrew), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun read_error_maps_to_StatsError() = runTest {
        val uc = ObserveMyMealCalendarUseCase(
            CalendarFakeActive(crewId),
            CalendarFakeSession(Session(me, null)),
            CalendarFakeRead(emptyList(), MealReadError.Unavailable),
            zone,
        )
        uc(flowOf(month)).test {
            assertEquals(Result.failure(StatsError.Read.Unavailable), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
