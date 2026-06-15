package es.schsebastian.foodrats.feature.ingredient.data.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CuisineMapperTest {

    private val validDto = CuisineDto(
        slug = "italian",
        names = mapOf("en" to "Italian", "es" to "Italiana"),
        iconKey = "italian",
    )

    @Test fun toDomain_succeeds_with_localized_name() {
        val cuisine = validDto.toDomain("es")!!
        assertEquals("italian", cuisine.slug.value)
        assertEquals("Italiana", cuisine.displayName)
        assertEquals("italian", cuisine.iconKey)
    }

    @Test fun toDomain_falls_back_to_english_when_language_missing() {
        assertEquals("Italian", validDto.toDomain("fr")!!.displayName)
    }

    @Test fun toDomain_falls_back_to_humanized_slug_when_no_localized_name() {
        // Neither the requested language nor 'en' present → humanized slug, never null.
        val dto = CuisineDto(slug = "middle_eastern", names = emptyMap())
        assertEquals("Middle eastern", dto.toDomain("en")!!.displayName)
    }

    @Test fun toDomain_defaults_iconKey_to_slug_when_absent() {
        val dto = CuisineDto(slug = "greek", names = mapOf("en" to "Greek"))
        assertEquals("greek", dto.toDomain("en")!!.iconKey)
    }

    @Test fun toDomain_returns_null_on_blank_slug() {
        assertNull(validDto.copy(slug = "").toDomain("en"))
        assertNull(validDto.copy(slug = "   ").toDomain("en"))
    }

    @Test fun toDomain_drops_overlong_slug_that_fails_validation() {
        val tooLong = "a".repeat(65)
        assertNull(validDto.copy(slug = tooLong).toDomain("en"))
    }
}
