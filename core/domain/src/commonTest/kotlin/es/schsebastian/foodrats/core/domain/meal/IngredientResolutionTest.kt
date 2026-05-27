package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientResolutionTest {

    @Test fun humanized_replaces_separators_and_capitalizes() {
        assertEquals("Chicken breast", IngredientSlug("chicken_breast").humanized())
        assertEquals("Olive oil", IngredientSlug("olive-oil").humanized())
        assertEquals("Egg", IngredientSlug("egg").humanized())
    }

    @Test fun resolver_uses_catalog_name_then_falls_back_to_humanized() {
        val catalog = mapOf(
            IngredientSlug("egg") to Ingredient(
                slug = IngredientSlug("egg"),
                displayName = "Huevo",
                category = IngredientCategory.Other,
            ),
        )
        val nameFor = ingredientNameResolver(catalog)
        assertEquals("Huevo", nameFor(IngredientSlug("egg")))
        assertEquals("Chicken breast", nameFor(IngredientSlug("chicken_breast")))
    }
}
