package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchIngredientsUseCaseTest {
    private val tomato = Ingredient(
        IngredientSlug("tomato"), "Tomate", IngredientCategory.Vegetable, null,
        aliases = listOf("cherry tomato"),
    )
    private val onion = Ingredient(
        IngredientSlug("onion"), "Cebolla", IngredientCategory.Vegetable,
    )
    private val uc = SearchIngredientsUseCase()

    @Test fun empty_query_returns_all() {
        assertEquals(2, uc(listOf(tomato, onion), "").size)
    }

    @Test fun matches_by_display_name() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "Tom"))
    }

    @Test fun matches_by_alias() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "cherry"))
    }
}
