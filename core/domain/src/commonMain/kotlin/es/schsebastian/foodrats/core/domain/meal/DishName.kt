package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class DishName private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 60
        fun of(raw: String): Result<DishName, MealValueObjectError> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.failure(MealValueObjectError.DishNameBlank)
                trimmed.length > MAX_LEN -> Result.failure(MealValueObjectError.DishNameTooLong)
                else                     -> Result.success(DishName(trimmed))
            }
        }
    }
}
