package es.schsebastian.foodrats.feature.meal.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `toWireAuthorName` guards the firestore.rules `authorName <= 120` cap (2026-07-19 hardening
 * sweep) at the meal-publish and comment-post wire seams. Locks the cap value, the pass-through
 * for compliant names, and the surrogate-pair boundary (an unpaired surrogate would corrupt the
 * write payload's UTF-8 serialization).
 */
class WireAuthorNameTest {

    @Test fun short_name_passes_through_unchanged() {
        assertEquals("Sebas", "Sebas".toWireAuthorName())
        assertEquals("", "".toWireAuthorName())
    }

    @Test fun name_at_exactly_the_cap_passes_through_unchanged() {
        val name = "a".repeat(AUTHOR_NAME_WIRE_MAX)
        assertEquals(name, name.toWireAuthorName())
    }

    @Test fun over_long_name_is_cut_to_the_cap() {
        val cut = "a".repeat(AUTHOR_NAME_WIRE_MAX + 1).toWireAuthorName()
        assertEquals(AUTHOR_NAME_WIRE_MAX, cut.length)
    }

    @Test fun cap_matches_the_deployed_rule() {
        assertEquals(120, AUTHOR_NAME_WIRE_MAX)
    }

    @Test fun truncation_never_leaves_an_unpaired_high_surrogate() {
        // 119 BMP chars then an emoji (surrogate pair) straddling the 120 boundary: a naive
        // take(120) would keep only the high surrogate. The safe cut drops it entirely.
        val name = "a".repeat(AUTHOR_NAME_WIRE_MAX - 1) + "🍕" + "tail"
        val cut = name.toWireAuthorName()
        assertEquals(AUTHOR_NAME_WIRE_MAX - 1, cut.length)
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test fun truncation_keeps_a_pair_that_fits_entirely() {
        // 118 BMP chars + full emoji = exactly 120 UTF-16 units before the tail — the pair fits.
        val name = "a".repeat(AUTHOR_NAME_WIRE_MAX - 2) + "🍕" + "tail"
        val cut = name.toWireAuthorName()
        assertEquals(AUTHOR_NAME_WIRE_MAX, cut.length)
        assertTrue(cut.endsWith("🍕"))
    }
}
