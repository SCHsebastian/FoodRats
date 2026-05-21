package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
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

class PersonalStreakTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 5, 16)
    private val me = (AccountId.of("me") as Result.Ok).value
    private val other = (AccountId.of("other") as Result.Ok).value

    private fun meal(author: AccountId, day: LocalDate) = Meal(
        (MealId.of("${author.value}-$day") as Result.Ok).value,
        MealAuthor(author, author.value, null),
        (CrewId.of("c") as Result.Ok).value,
        MealDay(day, zone),
        MealSlot.Lunch,
        "u",
        (DishName.of("Pasta") as Result.Ok).value,
        Description.EMPTY,
        Instant.fromEpochMilliseconds(0L),
    )

    @Test fun posted_today_streak_is_one() {
        assertEquals(1, computePersonalStreak(listOf(meal(me, today)), me, today).days)
    }

    @Test fun did_not_post_today_streak_is_zero() {
        assertEquals(0, computePersonalStreak(listOf(meal(me, today.minus(DatePeriod(days = 1)))), me, today).days)
    }

    @Test fun three_consecutive_days_streak_is_three() {
        val meals = (0..2).map { meal(me, today.minus(DatePeriod(days = it))) }
        assertEquals(3, computePersonalStreak(meals, me, today).days)
    }

    @Test fun other_users_meals_dont_count() {
        val meals = listOf(meal(other, today), meal(other, today.minus(DatePeriod(days = 1))))
        assertEquals(0, computePersonalStreak(meals, me, today).days)
    }

    @Test fun gap_breaks_the_streak() {
        val meals = listOf(meal(me, today), meal(me, today.minus(DatePeriod(days = 2))))
        assertEquals(1, computePersonalStreak(meals, me, today).days)
    }
}
