package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.location.Coordinates
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlin.time.Instant

data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    val slot: MealSlot,
    val photoUrl: String,
    val dish: DishName,
    val description: Description,
    val publishedAt: Instant,
    val coordinates: Coordinates? = null,
    val ingredients: List<IngredientSlug> = emptyList(),
    val detectedIngredients: List<IngredientSlug> = emptyList(),
    val classifierVersion: String? = null,
)

/**
 * The ingredients to surface for a meal: user-confirmed first, then AI-detected ones not
 * already confirmed, deduped (first occurrence wins). Shared by feed display and stats so
 * both count/show the same set.
 */
fun Meal.mergedIngredientSlugs(): List<IngredientSlug> =
    (ingredients + detectedIngredients).distinct()
