package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class Description private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 280
        val EMPTY = Description("")

        fun of(raw: String): Result<Description, MealValueObjectError> {
            val trimmed = raw.trim()
            return if (trimmed.length > MAX_LEN) Result.failure(MealValueObjectError.DescriptionTooLong)
            else Result.success(Description(trimmed))
        }
    }
}
