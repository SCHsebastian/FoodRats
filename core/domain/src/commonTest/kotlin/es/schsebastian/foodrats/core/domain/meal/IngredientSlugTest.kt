package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IngredientSlugTest {
    @Test fun rejects_blank() {
        assertFailsWith<IllegalArgumentException> { IngredientSlug("") }
        assertFailsWith<IllegalArgumentException> { IngredientSlug("   ") }
    }

    @Test fun rejects_too_long() {
        assertFailsWith<IllegalArgumentException> { IngredientSlug("a".repeat(65)) }
    }

    @Test fun accepts_normal_slug() {
        assertEquals("tomato", IngredientSlug("tomato").value)
    }
}
