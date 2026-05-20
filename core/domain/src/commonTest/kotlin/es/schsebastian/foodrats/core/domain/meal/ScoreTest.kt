package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScoreTest {
    @Test fun min_is_accepted() {
        val r = Score.of(1)
        assertIs<Result.Ok<Score>>(r)
        assertEquals(1, r.value.value)
    }

    @Test fun max_is_minotauro_6() {
        val r = Score.of(Score.MINOTAURO)
        assertIs<Result.Ok<Score>>(r)
        assertEquals(6, r.value.value)
    }

    @Test fun seven_is_rejected() {
        val r = Score.of(7)
        assertIs<Result.Err<MealValueObjectError>>(r)
        assertEquals(MealValueObjectError.ScoreOutOfRange, r.error)
    }

    @Test fun zero_is_rejected() {
        val r = Score.of(0)
        assertIs<Result.Err<MealValueObjectError>>(r)
    }
}
