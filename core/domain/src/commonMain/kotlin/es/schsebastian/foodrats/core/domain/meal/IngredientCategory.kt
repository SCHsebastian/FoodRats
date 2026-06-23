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

    companion object {
        /**
         * Canonical display order of every category — the single source of truth the
         * picker groups by (so the list is stable and every group is reachable).
         */
        val all: List<IngredientCategory> = listOf(
            Vegetable, Fruit, Meat, Fish, Dairy, Grain,
            Legume, Sauce, Spice, Sweet, Beverage, Other,
        )
    }
}
