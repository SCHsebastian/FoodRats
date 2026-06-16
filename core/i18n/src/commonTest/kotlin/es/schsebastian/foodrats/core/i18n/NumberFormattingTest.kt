package es.schsebastian.foodrats.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Locks the locale-independent, `%f`-free decimal formatting that votes/scores/coordinates must use
 * when passed to [resolve] (see [toFixed]'s KDoc for why `%n$.Nf` placeholders are forbidden).
 */
class NumberFormattingTest {

    @Test
    fun scores_format_to_one_decimal() {
        assertEquals("4.5", 4.5.toFixed(1))
        assertEquals("5.0", 5.0.toFixed(1))
        assertEquals("1.0", 1.0.toFixed(1))
        assertEquals("0.0", 0.0.toFixed(1))
    }

    @Test
    fun rounding_is_half_up_at_the_requested_precision() {
        assertEquals("4.3", 4.349.toFixed(1))
        assertEquals("4.0", 4.04.toFixed(1))
        assertEquals("5", 4.6.toFixed(0))
        // 0.25 is exactly representable, so the .5 tie at the 1-decimal place rounds up cleanly.
        assertEquals("0.3", 0.25.toFixed(1))
    }

    @Test
    fun trailing_zeros_are_kept_so_width_is_stable() {
        assertEquals("40.50000", 40.5.toFixed(5))
        assertEquals("3.10", 3.1.toFixed(2))
    }

    @Test
    fun negative_values_keep_their_sign_but_negative_zero_does_not() {
        assertEquals("-73.98543", (-73.985428).toFixed(5))
        assertEquals("-3.14", (-3.14159).toFixed(2))
        // Rounds to zero magnitude — must not render a "-0.0".
        assertEquals("0.0", (-0.04).toFixed(1))
    }

    @Test
    fun negative_decimals_is_rejected() {
        assertFailsWith<IllegalArgumentException> { 1.0.toFixed(-1) }
    }
}
