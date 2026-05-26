package es.schsebastian.foodrats.feature.ingredient.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class IngredientDto(
    val slug: String = "",
    val names: Map<String, String> = emptyMap(),
    val category: String = "Other",
    val iconKey: String? = null,
    val aliases: List<String> = emptyList(),
)
