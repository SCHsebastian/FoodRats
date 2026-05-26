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

/**
 * Display fallback for a slug absent from the localized catalog (catalog still loading,
 * or an unknown slug): `chicken_breast` -> `Chicken breast`. The real localized name
 * replaces this as soon as the catalog emits.
 */
fun IngredientSlug.humanized(): String =
    value.replace('_', ' ').replace('-', ' ').trim()
        .replaceFirstChar { it.uppercaseChar() }

/**
 * Builds a slug -> display-name resolver over a catalog snapshot, falling back to
 * [humanized] for any slug the catalog does not (yet) know. Shared by feed and stats so
 * both render ingredient names identically without depending on `:feature:ingredient`.
 */
fun ingredientNameResolver(
    catalog: Map<IngredientSlug, Ingredient>,
): (IngredientSlug) -> String = { slug -> catalog[slug]?.displayName ?: slug.humanized() }
