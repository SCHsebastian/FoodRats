package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug

fun IngredientDto.toDomain(currentLang: String): Ingredient? {
    if (slug.isBlank()) return null
    val name = names[currentLang] ?: names["en"] ?: return null
    val cat = categoryFromString(category)
    return Ingredient(
        slug = runCatching { IngredientSlug(slug) }.getOrNull() ?: return null,
        displayName = name,
        category = cat,
        iconKey = iconKey,
        aliases = aliases,
    )
}

private fun categoryFromString(raw: String): IngredientCategory = when (raw) {
    "Vegetable" -> IngredientCategory.Vegetable
    "Fruit"     -> IngredientCategory.Fruit
    "Meat"      -> IngredientCategory.Meat
    "Fish"      -> IngredientCategory.Fish
    "Dairy"     -> IngredientCategory.Dairy
    "Grain"     -> IngredientCategory.Grain
    "Legume"    -> IngredientCategory.Legume
    "Sauce"     -> IngredientCategory.Sauce
    "Spice"     -> IngredientCategory.Spice
    "Sweet"     -> IngredientCategory.Sweet
    "Beverage"  -> IngredientCategory.Beverage
    else        -> IngredientCategory.Other
}
