package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealRating
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardComputeTest {

    @Test
    fun meals_without_ratings_are_excluded_from_average_but_counted_in_postCount() {
        val rated = withRatings(authorId = "a", ratings = listOf(3, 5))      // avg 4
        val unrated = withRatings(authorId = "a", ratings = emptyList())     // ignored from avg
        val board = computeLeaderboard(listOf(rated, unrated))
        assertEquals(1, board.entries.size)
        assertEquals(2, board.entries[0].postCount)
        assertEquals(4.0, board.entries[0].averageScore, 1e-9)
    }

    @Test
    fun author_with_only_unrated_meals_is_excluded() {
        val rated = withRatings(authorId = "a", ratings = listOf(5))
        val onlyUnrated = withRatings(authorId = "b", ratings = emptyList())
        val board = computeLeaderboard(listOf(rated, onlyUnrated))
        assertEquals(1, board.entries.size)
        assertEquals("a", board.entries[0].accountId.value)
    }

    @Test
    fun entries_sort_by_average_desc_then_post_count_desc() {
        val a = withRatings(authorId = "a", ratings = listOf(5))
        val b1 = withRatings(authorId = "b", ratings = listOf(4))
        val b2 = withRatings(authorId = "b", ratings = listOf(4))
        val board = computeLeaderboard(listOf(b1, b2, a))
        assertEquals("a", board.entries[0].accountId.value)   // 5.0 wins over 4.0 regardless of count
        assertEquals("b", board.entries[1].accountId.value)
    }

    @Test
    fun empty_meals_empty_board() {
        assertEquals(emptyList(), computeLeaderboard(emptyList()).entries)
    }
}

private fun withRatings(authorId: String, ratings: List<Int>): MealWithRatings {
    val author = MealAuthor(
        (AccountId.of(authorId) as Result.Ok).value,
        displayName = authorId,
        avatarUrl = null,
    )
    val meal = Meal(
        id = (MealId.of("m-$authorId-${ratings.size}-${ratings.sum()}") as Result.Ok).value,
        author = author,
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-05-19"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "u",
        dish = (DishName.of("d") as Result.Ok).value,
        tags = emptyList(),
        publishedAt = Instant.parse("2026-05-19T12:00:00Z"),
    )
    val mealRatings = ratings.mapIndexed { i, s ->
        MealRating(
            raterId = (AccountId.of("r-$authorId-$i") as Result.Ok).value,
            raterDisplayName = "r$i",
            raterAvatarUrl = null,
            score = (Score.of(s) as Result.Ok).value,
            ratedAt = Instant.parse("2026-05-19T13:00:00Z"),
        )
    }
    return MealWithRatings(meal, mealRatings)
}
