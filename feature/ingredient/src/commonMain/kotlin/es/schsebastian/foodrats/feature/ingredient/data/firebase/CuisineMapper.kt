package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.cuisine.humanized
import es.schsebastian.foodrats.core.domain.result.getOrNull

/**
 * Maps a [CuisineDto] to its language-resolved [Cuisine], or `null` when the slug is malformed.
 * Name fallback mirrors [IngredientDto.toDomain]: active language → `en` → [CuisineSlug.humanized]
 * (so an unlocalized cuisine still renders a readable cell rather than vanishing).
 */
fun CuisineDto.toDomain(currentLang: String): Cuisine? {
    if (slug.isBlank()) return null
    val cuisineSlug = CuisineSlug.of(slug).getOrNull() ?: return null
    val name = names[currentLang] ?: names["en"] ?: cuisineSlug.humanized()
    return Cuisine(
        slug = cuisineSlug,
        displayName = name,
        iconKey = iconKey ?: slug,
    )
}
