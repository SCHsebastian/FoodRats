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
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardComputeTest {
    private fun m(author: AccountId, score: Int) = Meal(
        (MealId.of("${author.value}-$score") as Result.Ok).value,
        MealAuthor(author, author.value, null),
        (CrewId.of("c") as Result.Ok).value,
        MealDay(LocalDate(2026, 5, 16), TimeZone.UTC),
        MealSlot.Lunch,
        "u",
        (Score.of(score) as Result.Ok).value,
        (DishName.of("dish") as Result.Ok).value,
        emptyList(),
        Instant.fromEpochMilliseconds(0L),
    )

    @Test fun average_per_member_sorted_desc_with_postcount_tiebreak() {
        // a: avg (10+8)/2 = 9.0 with 2 posts; b: 9/1 = 9.0 with 1 post.
        // Sort: averageScore desc, then postCount desc, then displayName asc.
        // Tie on 9.0 → postCount tiebreak → a first.
        val a = (AccountId.of("a") as Result.Ok).value
        val b = (AccountId.of("b") as Result.Ok).value
        val board = computeLeaderboard(
            meals = listOf(m(a, 10), m(a, 8), m(b, 9)),
        ).entries
        assertEquals(2, board.size)
        assertEquals(a, board[0].accountId)
        assertEquals(9.0, board[0].averageScore)
        assertEquals(2, board[0].postCount)
        assertEquals(b, board[1].accountId)
    }

    @Test fun empty_meals_empty_board() {
        assertEquals(emptyList(), computeLeaderboard(emptyList()).entries)
    }
}
