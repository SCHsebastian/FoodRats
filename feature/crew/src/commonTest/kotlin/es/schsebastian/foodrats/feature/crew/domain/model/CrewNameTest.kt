package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrewNameTest {
    @Test fun of_valid_returns_Ok() {
        val r = CrewName.of("The Hungry Rats")
        assertIs<Result.Ok<CrewName>>(r)
        assertEquals("The Hungry Rats", r.value.value)
    }

    @Test fun of_trims_surrounding_whitespace() {
        val r = CrewName.of("  Rats  ")
        assertIs<Result.Ok<CrewName>>(r)
        assertEquals("Rats", r.value.value)
    }

    @Test fun of_blank_returns_NameBlank() {
        assertEquals(
            Result.failure(CrewError.Validation.NameBlank),
            CrewName.of(""),
        )
        assertEquals(
            Result.failure(CrewError.Validation.NameBlank),
            CrewName.of("   "),
        )
    }

    @Test fun of_too_long_returns_NameTooLong() {
        assertEquals(
            Result.failure(CrewError.Validation.NameTooLong),
            CrewName.of("a".repeat(CrewName.MAX + 1)),
        )
    }

    @Test fun of_at_max_len_returns_Ok() {
        val r = CrewName.of("a".repeat(CrewName.MAX))
        assertIs<Result.Ok<CrewName>>(r)
        assertEquals(CrewName.MAX, r.value.value.length)
    }
}
