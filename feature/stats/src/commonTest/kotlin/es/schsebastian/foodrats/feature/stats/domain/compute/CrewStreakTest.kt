package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class CrewStreakTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val alice = (AccountId.of("alice") as Result.Ok).value
    private val bob = (AccountId.of("bob") as Result.Ok).value
    private val carol = (AccountId.of("carol") as Result.Ok).value

    private fun meal(author: AccountId, day: LocalDate, dish: String = "Pasta"): Meal = Meal(
        id = (MealId.of("${author.value}-$day") as Result.Ok).value,
        author = MealAuthor(author, author.value, null),
        crewId = (CrewId.of("c") as Result.Ok).value,
        day = MealDay(day, zone),
        slot = MealSlot.Lunch,
        photoUrl = "u",
        score = (Score.of(5) as Result.Ok).value,
        dish = (DishName.of(dish) as Result.Ok).value,
        tags = emptyList(),
        publishedAt = Instant.fromEpochMilliseconds(0L),
    )

    private fun daysAgo(n: Int): LocalDate = today.minus(DatePeriod(days = n))

    @Test fun all_three_posted_today_streak_is_one() {
        val meals = listOf(meal(alice, today), meal(bob, today), meal(carol, today))
        assertEquals(1, computeCrewStreak(meals, listOf(alice, bob, carol), today, zone).days)
    }

    @Test fun all_three_posted_today_and_yesterday_streak_is_two() {
        val meals = listOf(
            meal(alice, today), meal(bob, today), meal(carol, today),
            meal(alice, daysAgo(1)), meal(bob, daysAgo(1)), meal(carol, daysAgo(1)),
        )
        assertEquals(2, computeCrewStreak(meals, listOf(alice, bob, carol), today, zone).days)
    }

    @Test fun one_member_missed_today_streak_is_zero() {
        val meals = listOf(meal(alice, today), meal(bob, today))  // carol missed
        assertEquals(0, computeCrewStreak(meals, listOf(alice, bob, carol), today, zone).days)
    }

    @Test fun break_in_history_stops_the_streak() {
        // today + day-1 + day-3 — day-2 is missed by one
        val meals = listOf(
            meal(alice, today), meal(bob, today), meal(carol, today),
            meal(alice, daysAgo(1)), meal(bob, daysAgo(1)), meal(carol, daysAgo(1)),
            meal(alice, daysAgo(2)), meal(bob, daysAgo(2)),  // carol missed day-2
            meal(alice, daysAgo(3)), meal(bob, daysAgo(3)), meal(carol, daysAgo(3)),
        )
        assertEquals(2, computeCrewStreak(meals, listOf(alice, bob, carol), today, zone).days)
    }

    @Test fun empty_meals_streak_is_zero() {
        assertEquals(0, computeCrewStreak(emptyList(), listOf(alice, bob, carol), today, zone).days)
    }

    @Test fun zero_members_returns_zero() {
        assertEquals(0, computeCrewStreak(emptyList(), emptyList(), today, zone).days)
    }
}
