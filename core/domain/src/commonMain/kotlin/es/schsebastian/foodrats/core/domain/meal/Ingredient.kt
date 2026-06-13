package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class IngredientSlug internal constructor(val value: String) {
    companion object {
        const val MAX_LEN = 64

        /**
         * Validating factory — the only public way to build an [IngredientSlug].
         * Blank → [MealValueObjectError.IngredientSlugBlank]; over [MAX_LEN] chars →
         * [MealValueObjectError.IngredientSlugTooLong]. Mirrors [DishName.of].
         */
        fun of(raw: String): Result<IngredientSlug, MealValueObjectError> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.failure(MealValueObjectError.IngredientSlugBlank)
                trimmed.length > MAX_LEN -> Result.failure(MealValueObjectError.IngredientSlugTooLong)
                else                     -> Result.success(IngredientSlug(trimmed))
            }
        }
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
