package es.schsebastian.foodrats.core.domain.meal

sealed interface MealValueObjectError {
    data object ScoreOutOfRange    : MealValueObjectError
    data object DishNameBlank      : MealValueObjectError
    data object DishNameTooLong    : MealValueObjectError
    data object MealIdBlank        : MealValueObjectError
    data object DescriptionTooLong : MealValueObjectError
}
