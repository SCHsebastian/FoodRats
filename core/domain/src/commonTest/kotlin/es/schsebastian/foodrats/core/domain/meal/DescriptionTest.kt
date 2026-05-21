package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DescriptionTest {
    @Test fun empty_input_returns_empty_description() {
        val r = Description.of("")
        assertTrue(r is Result.Ok)
        assertEquals("", r.value.value)
    }

    @Test fun whitespace_only_input_returns_empty_description() {
        val r = Description.of("   ")
        assertTrue(r is Result.Ok)
        assertEquals("", r.value.value)
    }

    @Test fun trims_leading_and_trailing_whitespace() {
        val r = Description.of("  hello  ")
        assertTrue(r is Result.Ok)
        assertEquals("hello", r.value.value)
    }

    @Test fun accepts_input_at_max_length_boundary() {
        val raw = "a".repeat(Description.MAX_LEN)
        val r = Description.of(raw)
        assertTrue(r is Result.Ok)
        assertEquals(Description.MAX_LEN, r.value.value.length)
    }

    @Test fun rejects_input_over_max_length() {
        val raw = "a".repeat(Description.MAX_LEN + 1)
        val r = Description.of(raw)
        assertTrue(r is Result.Err)
        assertEquals(MealValueObjectError.DescriptionTooLong, r.error)
    }

    @Test fun EMPTY_singleton_has_empty_value() {
        assertEquals("", Description.EMPTY.value)
    }

    @Test fun max_len_is_280() {
        assertEquals(280, Description.MAX_LEN)
    }
}
