package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class MealWithRatingsTest {
    private val author = MealAuthor(
        accountId = (AccountId.of("u-author") as Result.Ok).value,
        displayName = "Author",
        avatarUrl = null,
    )
    private val sampleMeal = Meal(
        id = (MealId.of("m1") as Result.Ok).value,
        author = author,
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-05-19"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "https://example.com/p.jpg",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        tags = emptyList(),
        publishedAt = Instant.parse("2026-05-19T12:00:00Z"),
    )

    private fun rating(rater: String, score: Int): MealRating = MealRating(
        raterId = (AccountId.of(rater) as Result.Ok).value,
        raterDisplayName = rater,
        raterAvatarUrl = null,
        score = (Score.of(score) as Result.Ok).value,
        ratedAt = Instant.parse("2026-05-19T13:00:00Z"),
    )

    @Test fun empty_ratings_have_null_average() {
        val agg = MealWithRatings(meal = sampleMeal, ratings = emptyList())
        assertNull(agg.averageScore)
        assertEquals(0, agg.ratingCount)
    }

    @Test fun average_is_mean_of_scores() {
        val agg = MealWithRatings(
            meal = sampleMeal,
            ratings = listOf(rating("a", 2), rating("b", 4), rating("c", 5)),
        )
        assertEquals(3.666666666666667, agg.averageScore!!, 1e-9)
        assertEquals(3, agg.ratingCount)
    }

    @Test fun ratingBy_returns_match_or_null() {
        val r = rating("a", 4)
        val agg = MealWithRatings(meal = sampleMeal, ratings = listOf(r))
        assertEquals(r, agg.ratingBy((AccountId.of("a") as Result.Ok).value))
        assertNull(agg.ratingBy((AccountId.of("nope") as Result.Ok).value))
    }
}
