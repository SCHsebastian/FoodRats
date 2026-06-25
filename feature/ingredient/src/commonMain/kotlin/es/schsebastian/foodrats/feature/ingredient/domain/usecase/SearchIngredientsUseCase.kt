package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient

/**
 * Searches the ingredient catalog. Matching is case-, accent- and punctuation-insensitive
 * and typo-tolerant — see [IngredientSearchIndex].
 *
 * The screen builds the [index] once per catalog snapshot (folding every name/alias is the
 * expensive part) and calls [IngredientSearchIndex.search] on each keystroke. The
 * `invoke(catalog, query)` overload is the one-shot convenience for tests and non-hot paths.
 */
class SearchIngredientsUseCase {

    /** Precompute the reusable index for a catalog snapshot — cache across keystrokes. */
    fun index(catalog: Collection<Ingredient>): IngredientSearchIndex =
        IngredientSearchIndex.from(catalog)

    operator fun invoke(catalog: Collection<Ingredient>, query: String): List<Ingredient> =
        IngredientSearchIndex.from(catalog).search(query)
}
