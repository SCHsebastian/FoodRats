package es.schsebastian.foodrats.core.domain.meal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyEmoteTest {

    @Test
    fun is_deterministic_for_same_dayKey() {
        val day = MealDay(LocalDate(2026, 5, 18), TimeZone.UTC)
        assertEquals(DailyEmote.forDay(day), DailyEmote.forDay(day))
    }

    @Test
    fun two_different_days_can_yield_different_emotes() {
        val unique = (1..30).map {
            DailyEmote.forDay(MealDay(LocalDate(2026, 5, it), TimeZone.UTC))
        }.toSet()
        assertTrue(unique.size >= 5, "Expected variety across 30 days, got $unique")
    }

    @Test
    fun is_a_nonempty_string() {
        val emote = DailyEmote.forDay(MealDay(LocalDate(2026, 5, 18), TimeZone.UTC))
        assertTrue(emote.isNotEmpty())
    }
}
