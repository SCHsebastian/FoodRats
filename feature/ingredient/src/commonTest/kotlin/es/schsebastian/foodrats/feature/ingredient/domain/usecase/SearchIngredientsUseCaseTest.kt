package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIngredientsUseCaseTest {
    private val tomato = Ingredient(
        IngredientSlug.of("tomato").getOrNull()!!, "Tomate", IngredientCategory.Vegetable, null,
        aliases = listOf("cherry tomato"),
    )
    private val onion = Ingredient(
        IngredientSlug.of("onion").getOrNull()!!, "Cebolla", IngredientCategory.Vegetable,
    )
    private val plantain = Ingredient(
        IngredientSlug.of("plantain").getOrNull()!!, "Plátano", IngredientCategory.Fruit,
    )
    private val jalapeno = Ingredient(
        IngredientSlug.of("jalapeno").getOrNull()!!, "Jalapeño", IngredientCategory.Vegetable,
    )
    private val uc = SearchIngredientsUseCase()

    @Test fun empty_query_returns_all() {
        assertEquals(2, uc(listOf(tomato, onion), "").size)
    }

    @Test fun blank_query_returns_all() {
        assertEquals(2, uc(listOf(tomato, onion), "   ").size)
    }

    @Test fun matches_by_display_name() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "Tom"))
    }

    @Test fun matches_by_alias() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "cherry"))
    }

    @Test fun is_case_insensitive() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "TOMATE"))
    }

    @Test fun ignores_accents() {
        // "platano" (un-accented) must reach "Plátano".
        assertEquals(listOf(plantain), uc(listOf(plantain, onion), "platano"))
    }

    @Test fun ignores_accents_on_n_tilde() {
        // "jalapeno" must reach "Jalapeño".
        assertEquals(listOf(jalapeno), uc(listOf(jalapeno, onion), "jalapeno"))
    }

    @Test fun ignores_surrounding_punctuation_and_whitespace() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "  ¡tomate!  "))
    }

    @Test fun tolerates_a_typo() {
        // One extra letter ("tomatte") still finds "Tomate".
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "tomatte"))
    }

    @Test fun tolerates_a_typo_on_alias() {
        // "chery" (missing one 'r') still finds the "cherry tomato" alias.
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "chery"))
    }

    @Test fun multi_word_query_narrows_with_and_semantics() {
        assertEquals(listOf(tomato), uc(listOf(tomato, onion), "cherry tomate"))
    }

    @Test fun unrelated_query_matches_nothing() {
        assertTrue(uc(listOf(tomato, onion), "xylophone").isEmpty())
    }

    @Test fun exact_name_match_ranks_before_a_fuzzy_one() {
        // Query "tomate" names `tomato` exactly; the English "Tomato" is one edit away,
        // so both match but the exact name must rank first.
        val tomatoEn = Ingredient(
            IngredientSlug.of("tomato_en").getOrNull()!!, "Tomato", IngredientCategory.Vegetable,
        )
        val results = uc(listOf(tomatoEn, tomato), "tomate")
        assertEquals(listOf(tomato, tomatoEn), results)
    }
}
