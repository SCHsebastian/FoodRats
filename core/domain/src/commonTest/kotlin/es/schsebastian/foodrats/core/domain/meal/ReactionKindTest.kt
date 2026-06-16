package es.schsebastian.foodrats.core.domain.meal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactionKindTest {

    @Test
    fun mvp_ships_exactly_one_kind() {
        assertEquals(listOf<ReactionKind>(ReactionKind.DailyGlyph), ReactionKind.all)
    }

    @Test
    fun every_kind_round_trips_through_its_persisted_key() {
        for (kind in ReactionKind.all) {
            assertEquals(kind, ReactionKind.fromKey(kind.key))
        }
    }

    @Test
    fun unknown_key_resolves_to_null_for_forward_compat() {
        assertNull(ReactionKind.fromKey("some_future_kind"))
    }

    @Test
    fun daily_glyph_key_is_stable_and_not_the_emoji() {
        assertEquals("daily_glyph", ReactionKind.DailyGlyph.key)
    }

    @Test
    fun daily_glyph_renders_as_the_meal_days_daily_emote() {
        // Contract: the rendered glyph for DailyGlyph is DailyEmote.forDay(meal.day),
        // derived at render time and never persisted on the reaction.
        val day = MealDay(LocalDate(2026, 5, 18), TimeZone.UTC)
        val glyph = DailyEmote.forDay(day)
        assertTrue(glyph.isNotEmpty())
        assertEquals(DailyEmote.forDay(day), glyph) // deterministic per day
    }
}
