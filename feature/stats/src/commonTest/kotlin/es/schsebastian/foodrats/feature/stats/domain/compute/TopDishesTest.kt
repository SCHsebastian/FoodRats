package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.stats.domain.model.DishTally
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TopDishesTest {
    private fun m(dish: String) = Meal(
        (MealId.of(dish) as Result.Ok).value,
        MealAuthor((AccountId.of("u") as Result.Ok).value, "u", null),
        (CrewId.of("c") as Result.Ok).value,
        MealDay(LocalDate(2026, 5, 16), TimeZone.UTC),
        "u",
        (Score.of(5) as Result.Ok).value,
        (DishName.of(dish) as Result.Ok).value,
        emptyList(),
        Instant.fromEpochMilliseconds(0L),
    )

    @Test fun groups_by_dish_and_sorts_descending() {
        val meals = listOf(m("Pasta"), m("Pasta"), m("Pizza"), m("Pasta"), m("Salad"), m("Pizza"))
        val top = computeTopDishes(meals, limit = 5)
        assertEquals(listOf(DishTally("Pasta", 3), DishTally("Pizza", 2), DishTally("Salad", 1)), top)
    }

    @Test fun respects_limit() {
        val meals = listOf(m("Alpha"), m("Alpha"), m("Beta"), m("Corn"))
        assertEquals(listOf(DishTally("Alpha", 2), DishTally("Beta", 1)), computeTopDishes(meals, limit = 2))
    }

    @Test fun empty_input_returns_empty() {
        assertEquals(emptyList(), computeTopDishes(emptyList(), limit = 5))
    }

    @Test fun ties_are_stable_alphabetically() {
        val meals = listOf(m("Banana"), m("Apple"))
        assertEquals(
            listOf(DishTally("Apple", 1), DishTally("Banana", 1)),
            computeTopDishes(meals, limit = 5),
        )
    }
}
