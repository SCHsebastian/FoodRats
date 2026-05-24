package es.schsebastian.foodrats.core.domain.meal

import kotlin.jvm.JvmInline

@JvmInline
value class IngredientSlug(val value: String) {
    init {
        require(value.isNotBlank()) { "IngredientSlug cannot be blank" }
        require(value.length <= 64) { "IngredientSlug too long: ${value.length}" }
    }
}

data class Ingredient(
    val slug: IngredientSlug,
    val displayName: String,
    val category: IngredientCategory,
    val iconKey: String? = null,
    val aliases: List<String> = emptyList(),
)
