package es.schsebastian.foodrats.feature.stats.domain.model

/** Crew-wide most-used ingredient in a window: resolved display name + number of meals using it. */
data class IngredientUsage(
    val displayName: String,
    val mealCount: Int,
)
