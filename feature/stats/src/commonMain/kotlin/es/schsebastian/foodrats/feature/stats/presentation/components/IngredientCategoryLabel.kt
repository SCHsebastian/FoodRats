package es.schsebastian.foodrats.feature.stats.presentation.components

import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

/**
 * Maps an [IngredientCategory] to its pokédex section-header [StatsStringKey]. Exhaustive `when` over
 * the sealed category interface, so adding a category forces a header string here at compile time.
 */
internal fun IngredientCategory.labelStringKey(): StatsStringKey = when (this) {
    IngredientCategory.Vegetable -> StatsStringKey.BingoCategoryVegetable
    IngredientCategory.Fruit -> StatsStringKey.BingoCategoryFruit
    IngredientCategory.Meat -> StatsStringKey.BingoCategoryMeat
    IngredientCategory.Fish -> StatsStringKey.BingoCategoryFish
    IngredientCategory.Dairy -> StatsStringKey.BingoCategoryDairy
    IngredientCategory.Grain -> StatsStringKey.BingoCategoryGrain
    IngredientCategory.Legume -> StatsStringKey.BingoCategoryLegume
    IngredientCategory.Sauce -> StatsStringKey.BingoCategorySauce
    IngredientCategory.Spice -> StatsStringKey.BingoCategorySpice
    IngredientCategory.Sweet -> StatsStringKey.BingoCategorySweet
    IngredientCategory.Beverage -> StatsStringKey.BingoCategoryBeverage
    IngredientCategory.Other -> StatsStringKey.BingoCategoryOther
}
