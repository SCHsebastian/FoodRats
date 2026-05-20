package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentTextTest {
    @Test fun rejects_blank() {
        assertTrue(CommentText.of("") is Result.Err)
        assertTrue(CommentText.of("   ") is Result.Err)
    }

    @Test fun trims_then_validates() {
        val r = CommentText.of("  hola  ")
        assertTrue(r is Result.Ok)
        assertEquals("hola", (r as Result.Ok).value.value)
    }

    @Test fun accepts_500_chars_but_rejects_501() {
        val ok = CommentText.of("a".repeat(500))
        assertTrue(ok is Result.Ok)
        val bad = CommentText.of("a".repeat(501))
        assertTrue(bad is Result.Err)
        assertEquals(CommentValidationError.TooLong, (bad as Result.Err).error)
    }

    @Test fun rejects_single_blank_emits_Blank_error() {
        val r = CommentText.of("")
        assertEquals(CommentValidationError.Blank, (r as Result.Err).error)
    }
}
