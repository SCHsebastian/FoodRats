package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IngredientSlugTest {
    @Test fun of_valid_returns_Ok() {
        val r = IngredientSlug.of("tomato")
        assertIs<Result.Ok<IngredientSlug>>(r)
        assertEquals("tomato", r.value.value)
    }

    @Test fun of_trims_surrounding_whitespace() {
        val r = IngredientSlug.of("  tomato  ")
        assertIs<Result.Ok<IngredientSlug>>(r)
        assertEquals("tomato", r.value.value)
    }

    @Test fun of_blank_returns_IngredientSlugBlank() {
        assertEquals(
            Result.failure(MealValueObjectError.IngredientSlugBlank),
            IngredientSlug.of(""),
        )
        assertEquals(
            Result.failure(MealValueObjectError.IngredientSlugBlank),
            IngredientSlug.of("   "),
        )
    }

    @Test fun of_too_long_returns_IngredientSlugTooLong() {
        assertEquals(
            Result.failure(MealValueObjectError.IngredientSlugTooLong),
            IngredientSlug.of("a".repeat(IngredientSlug.MAX_LEN + 1)),
        )
    }

    @Test fun of_at_max_len_returns_Ok() {
        val r = IngredientSlug.of("a".repeat(IngredientSlug.MAX_LEN))
        assertIs<Result.Ok<IngredientSlug>>(r)
        assertEquals(IngredientSlug.MAX_LEN, r.value.value.length)
    }
}
