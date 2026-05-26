package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class IngredientResolutionTest {

    private fun meal(
        confirmed: List<String> = emptyList(),
        detected: List<String> = emptyList(),
    ) = Meal(
        id = (MealId.of("m1") as Result.Ok).value,
        author = MealAuthor((AccountId.of("u") as Result.Ok).value, "U", null),
        crewId = (CrewId.of("c1") as Result.Ok).value,
        day = MealDay(LocalDate.parse("2026-05-19"), TimeZone.UTC),
        slot = MealSlot.Lunch,
        photoUrl = "p",
        dish = (DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        publishedAt = Instant.parse("2026-05-19T12:00:00Z"),
        ingredients = confirmed.map { IngredientSlug(it) },
        detectedIngredients = detected.map { IngredientSlug(it) },
    )

    @Test fun humanized_replaces_separators_and_capitalizes() {
        assertEquals("Chicken breast", IngredientSlug("chicken_breast").humanized())
        assertEquals("Olive oil", IngredientSlug("olive-oil").humanized())
        assertEquals("Egg", IngredientSlug("egg").humanized())
    }

    @Test fun merged_keeps_confirmed_first_then_detected_deduped() {
        val merged = meal(confirmed = listOf("egg", "rice"), detected = listOf("egg", "bacon"))
            .mergedIngredientSlugs()
            .map { it.value }
        assertEquals(listOf("egg", "rice", "bacon"), merged)
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
