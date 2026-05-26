package es.schsebastian.foodrats.feature.ingredient.data.firebase

import kotlinx.serialization.Serializable

@Serializable
data class DishIngredientMapDto(
    val dishSlug: String = "",
    val modelLabel: String = "",
    val defaultIngredients: List<String> = emptyList(),
)
