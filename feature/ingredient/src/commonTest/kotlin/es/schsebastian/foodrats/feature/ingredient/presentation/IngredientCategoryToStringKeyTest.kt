package es.schsebastian.foodrats.feature.ingredient.presentation

import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import kotlin.test.Test
import kotlin.test.assertNotNull

class IngredientCategoryToStringKeyTest {
    @Test fun all_categories_mapped() {
        val categories = listOf(
            IngredientCategory.Vegetable, IngredientCategory.Fruit, IngredientCategory.Meat,
            IngredientCategory.Fish, IngredientCategory.Dairy, IngredientCategory.Grain,
            IngredientCategory.Legume, IngredientCategory.Sauce, IngredientCategory.Spice,
            IngredientCategory.Sweet, IngredientCategory.Beverage, IngredientCategory.Other,
        )
        categories.forEach { assertNotNull(it.toStringKey()) }
    }
}
