package es.schsebastian.foodrats.core.domain.meal

sealed interface IngredientCategory {
    data object Vegetable : IngredientCategory
    data object Fruit     : IngredientCategory
    data object Meat      : IngredientCategory
    data object Fish      : IngredientCategory
    data object Dairy     : IngredientCategory
    data object Grain     : IngredientCategory
    data object Legume    : IngredientCategory
    data object Sauce     : IngredientCategory
    data object Spice     : IngredientCategory
    data object Sweet     : IngredientCategory
    data object Beverage  : IngredientCategory
    data object Other     : IngredientCategory
}
