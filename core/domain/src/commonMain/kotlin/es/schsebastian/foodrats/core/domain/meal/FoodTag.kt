package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result

sealed interface FoodTag {
    val label: String

    enum class Curated(override val label: String) : FoodTag {
        Breakfast("breakfast"), Lunch("lunch"), Dinner("dinner"),
        Snack("snack"), Brunch("brunch"), Dessert("dessert"),
        Drink("drink"), Other("other"),
    }

    value class Custom internal constructor(override val label: String) : FoodTag

    companion object {
        fun custom(raw: String): Result<Custom, MealValueObjectError> {
            val trimmed = raw.trim().lowercase()
            return if (trimmed.isEmpty()) Result.failure(MealValueObjectError.FoodTagBlank)
            else Result.success(Custom(trimmed))
        }
    }
}
