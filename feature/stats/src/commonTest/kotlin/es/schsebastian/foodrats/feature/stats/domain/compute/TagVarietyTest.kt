package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TagVarietyTest {
    private fun m(tags: List<String>) = Meal(
        (MealId.of(tags.joinToString().ifEmpty { "empty" }) as Result.Ok).value,
        MealAuthor((AccountId.of("u") as Result.Ok).value, "u", null),
        (CrewId.of("c") as Result.Ok).value,
        MealDay(LocalDate(2026, 5, 16), TimeZone.UTC),
        "u",
        (Score.of(5) as Result.Ok).value,
        (DishName.of("dish") as Result.Ok).value,
        tags.mapNotNull { (FoodTag.custom(it) as? Result.Ok)?.value },
        Instant.fromEpochMilliseconds(0L),
    )

    @Test fun distinct_tags_count() {
        val meals = listOf(m(listOf("italian", "dinner")), m(listOf("dinner", "comfort")))
        assertEquals(3, computeTagVariety(meals))
    }

    @Test fun ignores_case_and_trim() {
        val meals = listOf(m(listOf("Italian", "  italian  ")))
        assertEquals(1, computeTagVariety(meals))
    }

    @Test fun empty_meals_zero() = assertEquals(0, computeTagVariety(emptyList()))
}
