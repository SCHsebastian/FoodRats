package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrewCodeTest {
    @Test fun of_valid_6char_returns_Ok() {
        val r = CrewCode.of("ABCD23")
        assertIs<Result.Ok<CrewCode>>(r)
        assertEquals("ABCD23", r.value.value)
    }

    @Test fun of_trims_and_uppercases() {
        val r = CrewCode.of("  abcd23  ")
        assertIs<Result.Ok<CrewCode>>(r)
        assertEquals("ABCD23", r.value.value)
    }

    @Test fun of_short_returns_Malformed() {
        assertEquals(
            Result.failure(CrewError.Validation.CodeMalformed),
            CrewCode.of("ABCD2"),
        )
    }

    @Test fun of_ambiguous_char_returns_Malformed() {
        // 'O' and '0' are excluded by the alphabet.
        assertEquals(
            Result.failure(CrewError.Validation.CodeMalformed),
            CrewCode.of("ABCD2O"),
        )
        assertEquals(
            Result.failure(CrewError.Validation.CodeMalformed),
            CrewCode.of("ABCD20"),
        )
    }

    @Test fun of_lowercase_letter_outside_alphabet_returns_Malformed() {
        // After uppercasing 'i' becomes 'I' which is excluded.
        assertEquals(
            Result.failure(CrewError.Validation.CodeMalformed),
            CrewCode.of("ABCDIi"),
        )
    }
}
