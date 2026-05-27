package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
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

    /** Tests resolve a slug to its raw value; the real catalog substitutes a localized name. */
    private val ingredientName: (IngredientSlug) -> String = { it.value }

    @Test fun empty_meals_yield_zero_totals_and_null_awards() {
        val out = computeWindow(emptyList(), window, ingredientName)
        assertEquals(0, out.totalMeals)
        assertEquals(0.0, out.avgPerDay)
        assertNull(out.bestMeal)
        assertNull(out.mostVotedMeal)
        assertNull(out.mostProlific)
        assertNull(out.bestCook)
        assertNull(out.mostCriticized)
        assertEquals(emptyList<Int>(), out.dailyMeals)
    }

    @Test fun daily_meals_series_is_chronological_with_zero_filled_gaps() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19)),
            mealWithRatings("m2", "bob",   LocalDate(2026, 5, 19)),
            mealWithRatings("m3", "carl",  LocalDate(2026, 5, 21)),
        )
        // 5/19 → 2 meals, 5/20 → 0 (zero-filled gap), 5/21 → 1
        assertEquals(listOf(2, 0, 1), computeWindow(meals, window, ingredientName).dailyMeals)
    }

    @Test fun best_meal_picks_highest_score_then_rating_count() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5, 5)),     // avg 5
            mealWithRatings("m2", "bob",   LocalDate(2026, 5, 20), ratings = listOf(5, 5, 5)),  // avg 5, more votes
            mealWithRatings("m3", "carl",  LocalDate(2026, 5, 21), ratings = listOf(4)),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals(mid("m2"), out.bestMeal?.mealId)
    }

    @Test fun most_voted_picks_highest_rating_count_then_score() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5)),
            mealWithRatings("m2", "bob",   LocalDate(2026, 5, 20), ratings = listOf(3, 3, 3)),
            mealWithRatings("m3", "carl",  LocalDate(2026, 5, 21), ratings = listOf(5, 5)),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals(mid("m2"), out.mostVotedMeal?.mealId)
    }

    @Test fun most_prolific_counts_plates_per_author() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19)),
            mealWithRatings("m2", "alice", LocalDate(2026, 5, 20)),
            mealWithRatings("m3", "bob",   LocalDate(2026, 5, 21)),
        )
        val out = computeWindow(meals, window, ingredientName)
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
        val out = computeWindow(meals, window, ingredientName)
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
        val out = computeWindow(meals, window, ingredientName)
        assertNotNull(out.bestCook)
        assertNull(out.mostCriticized)
    }

    @Test fun avg_per_day_uses_window_days_not_unique_days() {
        val meals = listOf(mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ratings = listOf(5)))
        val out = computeWindow(meals, window, ingredientName)
        assertTrue(abs(out.avgPerDay - (1.0 / 7.0)) < 1e-6)
    }

    @Test fun no_ingredients_yields_null_most_used_and_empty_per_member() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19)),
            mealWithRatings("m2", "bob", LocalDate(2026, 5, 20)),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertNull(out.mostUsedIngredient)
        assertTrue(out.topByMember.isEmpty())
    }

    @Test fun most_used_ingredient_counts_meals_using_it() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ingredients = listOf("egg", "rice")),
            mealWithRatings("m2", "bob", LocalDate(2026, 5, 20), ingredients = listOf("egg", "bacon")),
            mealWithRatings("m3", "carl", LocalDate(2026, 5, 21), ingredients = listOf("egg")),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals("egg", out.mostUsedIngredient?.displayName)
        assertEquals(3, out.mostUsedIngredient?.mealCount)
    }

    @Test fun most_used_ingredient_tie_breaks_on_name_ascending() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ingredients = listOf("rice")),
            mealWithRatings("m2", "bob", LocalDate(2026, 5, 20), ingredients = listOf("apple")),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals("apple", out.mostUsedIngredient?.displayName)
        assertEquals(1, out.mostUsedIngredient?.mealCount)
    }

    @Test fun duplicate_slug_within_a_meal_counts_once() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ingredients = listOf("egg", "egg")),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals(1, out.mostUsedIngredient?.mealCount)
    }

    @Test fun top_by_member_returns_each_authors_own_top_ingredient() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ingredients = listOf("egg")),
            mealWithRatings("m2", "alice", LocalDate(2026, 5, 20), ingredients = listOf("egg", "rice")),
            mealWithRatings("m3", "bob", LocalDate(2026, 5, 21), ingredients = listOf("bacon")),
        )
        val out = computeWindow(meals, window, ingredientName)
        val alice = out.topByMember.first { it.accountId == acct("alice") }
        val bob = out.topByMember.first { it.accountId == acct("bob") }
        assertEquals("egg", alice.ingredientName)
        assertEquals(2, alice.mealCount)
        assertEquals("bacon", bob.ingredientName)
        assertEquals(1, bob.mealCount)
    }

    @Test fun ai_detected_ingredients_are_excluded_from_stats() {
        // Only user-confirmed ingredients count; AI-detected-but-unconfirmed ones must not.
        val meals = listOf(
            mealWithRatings(
                "m1", "alice", LocalDate(2026, 5, 19),
                ingredients = listOf("egg"),
                detectedIngredients = listOf("bacon"),
            ),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals("egg", out.mostUsedIngredient?.displayName)
        assertEquals(1, out.mostUsedIngredient?.mealCount)
        val alice = out.topByMember.first { it.accountId == acct("alice") }
        assertEquals("egg", alice.ingredientName)
        assertEquals(listOf("egg"), out.topByMember.map { it.ingredientName })
    }

    @Test fun top_by_member_omits_authors_without_ingredients() {
        val meals = listOf(
            mealWithRatings("m1", "alice", LocalDate(2026, 5, 19), ingredients = listOf("egg")),
            mealWithRatings("m2", "bob", LocalDate(2026, 5, 20)),
        )
        val out = computeWindow(meals, window, ingredientName)
        assertEquals(listOf(acct("alice")), out.topByMember.map { it.accountId })
    }
}
