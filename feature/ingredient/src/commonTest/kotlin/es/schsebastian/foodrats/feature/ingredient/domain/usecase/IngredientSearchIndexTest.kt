package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientSearchIndexTest {

    @Test fun normalize_lowercases_and_folds_accents() {
        assertEquals("platano", normalizeForSearch("Plátano"))
        assertEquals("jalapeno", normalizeForSearch("JALAPEÑO"))
        assertEquals("creme fraiche", normalizeForSearch("Crème Fraîche"))
    }

    @Test fun normalize_collapses_punctuation_and_whitespace_to_single_spaces() {
        assertEquals("creme fraiche", normalizeForSearch("  crème-fraîche!! "))
        assertEquals("salt pepper", normalizeForSearch("salt & pepper"))
        assertEquals("", normalizeForSearch("  -- !! "))
    }

    @Test fun bounded_distance_returns_distance_within_cap() {
        assertEquals(0, boundedDistance("tomate", "tomate", 2))
        assertEquals(1, boundedDistance("tomate", "tomste", 2)) // one substitution
        assertEquals(1, boundedDistance("tomate", "tomatte", 2)) // one insertion
    }

    @Test fun bounded_distance_bails_to_minus_one_past_the_cap() {
        assertEquals(-1, boundedDistance("tomate", "cebolla", 2))
        assertEquals(-1, boundedDistance("ab", "abcd", 1)) // length gap exceeds cap
    }

    @Test fun index_search_is_blank_query_passthrough_in_catalog_order() {
        val a = veg("apple", "Apple")
        val b = veg("banana", "Banana")
        val index = IngredientSearchIndex.from(listOf(a, b))
        assertEquals(listOf(a, b), index.search(""))
        assertEquals(listOf(a, b), index.all)
    }

    private fun veg(slug: String, name: String) =
        Ingredient(IngredientSlug.of(slug).getOrNull()!!, name, IngredientCategory.Vegetable)
}
