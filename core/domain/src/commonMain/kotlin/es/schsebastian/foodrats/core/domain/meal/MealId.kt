package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result

value class MealId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Result<MealId, MealValueObjectError> {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) Result.failure(MealValueObjectError.MealIdBlank)
            else Result.success(MealId(trimmed))
        }
    }
}
