package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BioTest {

    @Test fun empty_input_returns_ok_null() {
        val r = Bio.of("")
        assertIs<Result.Ok<Bio?>>(r)
        assertNull(r.value)
    }

    @Test fun whitespace_only_returns_ok_null() {
        val r = Bio.of("   ")
        assertIs<Result.Ok<Bio?>>(r)
        assertNull(r.value)
    }

    @Test fun valid_bio_returns_trimmed_value() {
        val r = Bio.of("  Home cook  ")
        assertIs<Result.Ok<Bio?>>(r)
        assertEquals("Home cook", r.value?.value)
    }

    @Test fun exactly_at_cap_returns_ok() {
        val atCap = "a".repeat(Bio.MAX_LENGTH)
        val r = Bio.of(atCap)
        assertIs<Result.Ok<Bio?>>(r)
        assertEquals(Bio.MAX_LENGTH, r.value?.value?.length)
    }

    @Test fun over_cap_returns_too_long_error() {
        val overCap = "a".repeat(Bio.MAX_LENGTH + 1)
        val r = Bio.of(overCap)
        assertIs<Result.Err<BioError>>(r)
        assertEquals(BioError.Validation.TooLong, r.error)
    }

    @Test fun max_length_is_100() {
        assertEquals(100, Bio.MAX_LENGTH)
    }
}
