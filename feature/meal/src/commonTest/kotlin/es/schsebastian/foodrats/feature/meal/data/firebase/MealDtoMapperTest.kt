package es.schsebastian.foodrats.feature.meal.data.firebase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MealDtoMapperTest {
    private fun baseMeal() = Meal(
        id = (MealId.of("m-1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("a-1") as Result.Ok).value, "Sam", null),
        crewId = (CrewId.of("c-1") as Result.Ok).value,
        day = MealDay(LocalDate(2026, 5, 16), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "https://x.png",
        dish = (DishName.of("Pizza") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.fromEpochMilliseconds(1_731_000_000_000),
    )

    private fun baseDto() = MealDto(
        id = "m-1", authorId = "a-1", authorName = "Sam", crewId = "c-1",
        dayKey = "2026-05-16", platePath = "crews/c-1/meals/m-1.jpg", dishName = "Pizza",
        publishedAtEpochMs = 1_731_000_000_000,
    )

    @Test fun round_trips_confirmed_ingredients_and_version() {
        val meal = baseMeal().copy(
            ingredients = listOf(IngredientSlug.of("tomato").getOrNull()!!, IngredientSlug.of("pasta").getOrNull()!!),
            classifierVersion = "food101-v1",
        )
        val back = (MealDto.from(meal).toDomain() as Result.Ok).value
        assertEquals(meal.ingredients, back.ingredients)
        assertEquals("food101-v1", back.classifierVersion)
    }

    @Test fun detected_ingredients_are_not_persisted() {
        // The raw classifier detection is a compose-time picker seed only; it must
        // never survive the DTO round-trip onto a published Meal.
        val meal = baseMeal().copy(
            ingredients = listOf(IngredientSlug.of("tomato").getOrNull()!!),
            detectedIngredients = listOf(IngredientSlug.of("cheese").getOrNull()!!, IngredientSlug.of("bacon").getOrNull()!!),
        )
        val back = (MealDto.from(meal).toDomain() as Result.Ok).value
        assertEquals(listOf(IngredientSlug.of("tomato").getOrNull()!!), back.ingredients)
        assertTrue(back.detectedIngredients.isEmpty())
    }

    @Test fun preserves_unknown_slugs() {
        val back = (baseDto().copy(ingredients = listOf("tomato", "xyz-not-in-catalog")).toDomain() as Result.Ok).value
        assertEquals(2, back.ingredients.size)
    }

    @Test fun blanks_dropped() {
        val back = (baseDto().copy(ingredients = listOf("", "tomato")).toDomain() as Result.Ok).value
        assertEquals(1, back.ingredients.size)
    }
}
