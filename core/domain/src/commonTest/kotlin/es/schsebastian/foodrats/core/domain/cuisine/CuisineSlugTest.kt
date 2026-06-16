package es.schsebastian.foodrats.core.domain.cuisine

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CuisineSlugTest {
    @Test fun of_valid_returns_Ok() {
        val r = CuisineSlug.of("italian")
        assertIs<Result.Ok<CuisineSlug>>(r)
        assertEquals("italian", r.value.value)
    }

    @Test fun of_trims_surrounding_whitespace() {
        val r = CuisineSlug.of("  middle_eastern  ")
        assertIs<Result.Ok<CuisineSlug>>(r)
        assertEquals("middle_eastern", r.value.value)
    }

    @Test fun of_blank_returns_CuisineSlugBlank() {
        assertEquals(
            Result.failure(CuisineValueObjectError.CuisineSlugBlank),
            CuisineSlug.of(""),
        )
        assertEquals(
            Result.failure(CuisineValueObjectError.CuisineSlugBlank),
            CuisineSlug.of("   "),
        )
    }

    @Test fun of_too_long_returns_CuisineSlugTooLong() {
        assertEquals(
            Result.failure(CuisineValueObjectError.CuisineSlugTooLong),
            CuisineSlug.of("a".repeat(CuisineSlug.MAX_LEN + 1)),
        )
    }

    @Test fun of_at_max_len_returns_Ok() {
        val r = CuisineSlug.of("a".repeat(CuisineSlug.MAX_LEN))
        assertIs<Result.Ok<CuisineSlug>>(r)
        assertEquals(CuisineSlug.MAX_LEN, r.value.value.length)
    }
}
