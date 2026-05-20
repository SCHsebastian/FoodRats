package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class Score private constructor(val value: Int) {
    companion object {
        const val MIN = 1
        const val MAX = 6
        const val MINOTAURO = 6
        fun of(value: Int): Result<Score, MealValueObjectError> =
            if (value in MIN..MAX) Result.success(Score(value))
            else Result.failure(MealValueObjectError.ScoreOutOfRange)
    }
}
