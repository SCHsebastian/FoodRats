package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.feature.stats.domain.model.StatsWindow
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeWindowTest {

    private val window = StatsWindow(Tab.Week, LocalDate(2026, 5, 18), LocalDate(2026, 5, 24), 7)

    @Test fun empty_meals_yield_zero_totals_and_null_awards() {
        val out = computeWindow(emptyList(), window)
        assertEquals(0, out.totalMeals)
        assertEquals(0.0, out.avgPerDay)
        assertNull(out.bestMeal)
        assertNull(out.mostVotedMeal)
        assertNull(out.mostProlific)
        assertNull(out.bestCook)
        assertNull(out.mostCriticized)
    }

    @Test fun best_meal_picks_highest_score_then_rating_count() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5, 5)),     // avg 5
            mealWithRatings("m2", "bob",   LocalDate(2026, 5, 20), ratings = listOf(5, 5, 5)),  // avg 5, more votes
            mealWithRatings("m3", "carl",  LocalDate(2026, 5, 21), ratings = listOf(4)),
        )
        val out = computeWindow(meals, window)
        assertEquals(mid("m2"), out.bestMeal?.mealId)
    }

    @Test fun most_voted_picks_highest_rating_count_then_score() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5)),
            mealWithRatings("m2", "bob",   LocalDate(2026, 5, 20), ratings = listOf(3, 3, 3)),
            mealWithRatings("m3", "carl",  LocalDate(2026, 5, 21), ratings = listOf(5, 5)),
        )
        val out = computeWindow(meals, window)
        assertEquals(mid("m2"), out.mostVotedMeal?.mealId)
    }

    @Test fun most_prolific_counts_plates_per_author() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19)),
            mealWithRatings("m2", "alice", LocalDate(2026, 5, 20)),
            mealWithRatings("m3", "bob",   LocalDate(2026, 5, 21)),
        )
        val out = computeWindow(meals, window)
        assertEquals(acct("alice"), out.mostProlific?.accountId)
        assertEquals(2, out.mostProlific?.mealCount)
    }

    @Test fun best_and_most_criticized_require_three_plates() {
        val meals = listOf(
            // alice: 3 plates, avg 5
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5)),
            mealWithRatings("m2", "alice", LocalDate(2026, 5, 20), ratings = listOf(5)),
            mealWithRatings("m3", "alice", LocalDate(2026, 5, 21), ratings = listOf(5)),
            // bob: 3 plates, avg 2
            mealWithRatings("m4", "bob", LocalDate(2026, 5, 19), ratings = listOf(2)),
            mealWithRatings("m5", "bob", LocalDate(2026, 5, 20), ratings = listOf(2)),
            mealWithRatings("m6", "bob", LocalDate(2026, 5, 21), ratings = listOf(2)),
            // carl: 1 plate, avg 1 — excluded from cook awards
            mealWithRatings("m7", "carl", LocalDate(2026, 5, 19), ratings = listOf(1)),
        )
        val out = computeWindow(meals, window)
        assertEquals(acct("alice"), out.bestCook?.accountId)
        assertEquals(acct("bob"), out.mostCriticized?.accountId)
    }

    @Test fun most_criticized_hidden_when_only_one_qualifies() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(4)),
            mealWithRatings("m2", "alice", LocalDate(2026, 5, 20), ratings = listOf(4)),
            mealWithRatings("m3", "alice", LocalDate(2026, 5, 21), ratings = listOf(4)),
            mealWithRatings("m4", "bob",   LocalDate(2026, 5, 22), ratings = listOf(4)),
        )
        val out = computeWindow(meals, window)
        assertNotNull(out.bestCook)
        assertNull(out.mostCriticized)
    }

    @Test fun avg_per_day_uses_window_days_not_unique_days() {
        val meals = listOf(mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5)))
        val out = computeWindow(meals, window)
        assertTrue(abs(out.avgPerDay - (1.0 / 7.0)) < 1e-6)
    }
}
