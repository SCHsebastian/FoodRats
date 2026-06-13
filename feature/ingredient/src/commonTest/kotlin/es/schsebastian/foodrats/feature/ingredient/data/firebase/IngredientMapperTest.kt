package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.meal.IngredientCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngredientMapperTest {

    private val validDto = IngredientDto(
        slug = "tomato",
        names = mapOf("en" to "Tomato", "es" to "Tomate"),
        category = "Vegetable",
        iconKey = "veg/tomato",
        aliases = listOf("tomate", "pomodoro"),
    )

    @Test fun toDomain_succeeds_with_localized_name() {
        val ing = validDto.toDomain("es")!!
        assertEquals("tomato", ing.slug.value)
        assertEquals("Tomate", ing.displayName)
        assertEquals(IngredientCategory.Vegetable, ing.category)
        assertEquals("veg/tomato", ing.iconKey)
        assertEquals(listOf("tomate", "pomodoro"), ing.aliases)
    }

    @Test fun toDomain_falls_back_to_english_when_language_missing() {
        // No 'fr' name → fall back to the 'en' entry.
        assertEquals("Tomato", validDto.toDomain("fr")!!.displayName)
    }

    @Test fun toDomain_returns_null_when_neither_language_nor_english_present() {
        val dto = validDto.copy(names = mapOf("es" to "Tomate"))
        assertNull(dto.toDomain("fr"))
    }

    @Test fun toDomain_returns_null_on_blank_slug() {
        assertNull(validDto.copy(slug = "").toDomain("en"))
    }

    @Test fun toDomain_returns_null_when_slug_is_whitespace_only() {
        // slug.isBlank() guards before IngredientSlug.of even runs.
        assertNull(validDto.copy(slug = "   ").toDomain("en"))
    }

    @Test fun toDomain_drops_overlong_slug_that_fails_validation() {
        // IngredientSlug.of rejects > MAX_LEN (64) → getOrNull() drops it → mapper returns null.
        val tooLong = "a".repeat(65)
        assertNull(validDto.copy(slug = tooLong).toDomain("en"))
    }

    @Test fun toDomain_parses_every_known_category() {
        val cases = mapOf(
            "Vegetable" to IngredientCategory.Vegetable,
            "Fruit" to IngredientCategory.Fruit,
            "Meat" to IngredientCategory.Meat,
            "Fish" to IngredientCategory.Fish,
            "Dairy" to IngredientCategory.Dairy,
            "Grain" to IngredientCategory.Grain,
            "Legume" to IngredientCategory.Legume,
            "Sauce" to IngredientCategory.Sauce,
            "Spice" to IngredientCategory.Spice,
            "Sweet" to IngredientCategory.Sweet,
            "Beverage" to IngredientCategory.Beverage,
            "Other" to IngredientCategory.Other,
        )
        for ((raw, expected) in cases) {
            assertEquals(expected, validDto.copy(category = raw).toDomain("en")!!.category, "category=$raw")
        }
    }

    @Test fun toDomain_maps_unknown_category_string_to_other() {
        assertEquals(IngredientCategory.Other, validDto.copy(category = "Plutonium").toDomain("en")!!.category)
    }

    @Test fun toDomain_defaults_icon_and_aliases_when_absent() {
        val dto = IngredientDto(slug = "salt", names = mapOf("en" to "Salt"))
        val ing = dto.toDomain("en")!!
        assertNull(ing.iconKey)
        assertEquals(emptyList(), ing.aliases)
    }
}
