package es.schsebastian.foodrats.feature.feed.presentation.components

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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedMealUiTest {
    @Test fun maps_meal_to_ui_dto() {
        val meal = Meal(
            id = (MealId.of("m-1") as Result.Ok).value,
            author = MealAuthor((AccountId.of("u-1") as Result.Ok).value, "Sam", "https://x/avatar.png"),
            crewId = (CrewId.of("c-1") as Result.Ok).value,
            day = MealDay(LocalDate(2026, 5, 16), TimeZone.UTC),
            photoUrl = "https://x/p.jpg",
            score = (Score.of(8) as Result.Ok).value,
            dish = (DishName.of("Pasta carbonara") as Result.Ok).value,
            tags = listOf(
                (FoodTag.custom("italian") as Result.Ok).value,
                (FoodTag.custom("dinner") as Result.Ok).value,
            ),
            publishedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        )
        val ui = meal.toFeedUi()
        assertEquals("m-1", ui.id)
        assertEquals("Sam", ui.authorName)
        assertEquals("https://x/avatar.png", ui.authorAvatarUrl)
        assertEquals("https://x/p.jpg", ui.photoUrl)
        assertEquals(8, ui.score)
        assertEquals("Pasta carbonara", ui.dishName)
        assertEquals(listOf("italian", "dinner"), ui.tags)
        assertEquals(1_700_000_000_000L, ui.publishedAtEpochMs)
    }
}
