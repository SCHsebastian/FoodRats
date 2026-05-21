package es.schsebastian.foodrats.feature.stats.domain.compute

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputeHeroTest {

    private val today = LocalDate(2026, 5, 21)

    @Test fun empty_meals_zero_today_no_streaks() {
        val hero = computeHeroStats(emptyList(), acct("me"), today)
        assertEquals(0, hero.platesToday)
        assertFalse(hero.iPostedToday)
        assertEquals(0, hero.personalStreak.days)
        assertEquals(0, hero.crewStreak.days)
    }

    @Test fun plates_today_counts_meals_on_today_in_crew() {
        val meals = listOf(
            mealWithRatings("m1", "me", today),
            mealWithRatings("m2", "alice", today),
            mealWithRatings("m3", "bob", LocalDate(2026, 5, 20)),
        )
        val hero = computeHeroStats(meals, acct("me"), today)
        assertEquals(2, hero.platesToday)
        assertTrue(hero.iPostedToday)
    }

    @Test fun i_posted_today_false_when_only_others_posted_today() {
        val meals = listOf(mealWithRatings("m1", "alice", today))
        val hero = computeHeroStats(meals, acct("me"), today)
        assertEquals(1, hero.platesToday)
        assertFalse(hero.iPostedToday)
    }
}
