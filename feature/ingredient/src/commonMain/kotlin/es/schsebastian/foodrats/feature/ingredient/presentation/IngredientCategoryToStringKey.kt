package es.schsebastian.foodrats.feature.ingredient.presentation

import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.feature.ingredient.i18n.IngredientStringKey

fun IngredientCategory.toStringKey(): IngredientStringKey = when (this) {
    IngredientCategory.Vegetable -> IngredientStringKey.CategoryVegetable
    IngredientCategory.Fruit     -> IngredientStringKey.CategoryFruit
    IngredientCategory.Meat      -> IngredientStringKey.CategoryMeat
    IngredientCategory.Fish      -> IngredientStringKey.CategoryFish
    IngredientCategory.Dairy     -> IngredientStringKey.CategoryDairy
    IngredientCategory.Grain     -> IngredientStringKey.CategoryGrain
    IngredientCategory.Legume    -> IngredientStringKey.CategoryLegume
    IngredientCategory.Sauce     -> IngredientStringKey.CategorySauce
    IngredientCategory.Spice     -> IngredientStringKey.CategorySpice
    IngredientCategory.Sweet     -> IngredientStringKey.CategorySweet
    IngredientCategory.Beverage  -> IngredientStringKey.CategoryBeverage
    IngredientCategory.Other     -> IngredientStringKey.CategoryOther
}
