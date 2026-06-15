package es.schsebastian.foodrats.core.domain.cuisine

import kotlinx.coroutines.flow.Flow

/**
 * Cross-context read contract for the cuisine catalog (`cuisines/{slug}`) and the
 * Food-101 dish → cuisine map (`dishCuisineMap/{dishSlug}`). Declared in `:core:domain`
 * — not in a feature — so the cuisine-passport feature's data layer implements it and any
 * consumer (passport grid, achievements' cuisine-explorer hook) reads through it without a
 * cross-feature dependency. Mirrors [IngredientReadPort][es.schsebastian.foodrats.core.domain.meal.IngredientReadPort].
 *
 * The Firebase adapter (`w2-cuisine-passport-presentation`) mirrors the ingredient catalog
 * read path EXACTLY: [observeCatalog] is a Firestore snapshot listener over `cuisines`
 * (language-resolved names, re-mapped off a `language: Flow<String>` like `IngredientRepository`);
 * [loadDishCuisine] is a one-shot `document(dishSlug).get()` over `dishCuisineMap`.
 */
interface CuisineReadPort {
    /**
     * The full cuisine catalog as a live map keyed by slug, names already resolved for the
     * active language. Emits a fresh snapshot when the catalog or the active language changes.
     */
    fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>>

    /**
     * Resolves a Food-101 dish slug (the classifier `categoryName()`) to its single mapped
     * cuisine, or `null` when the dish is absent from `dishCuisineMap`. One-shot; used to
     * STAMP the cuisine onto a `Meal` at publish (see the stamp contract in
     * `docs/session/handoffs/w2-cuisine-passport-domain.md`).
     */
    suspend fun loadDishCuisine(dishSlug: String): CuisineSlug?
}

/**
 * Typed failures of cuisine reads. Sealed interface with `data object` leaves — never an
 * enum — so a payload can be attached later. The consuming feature folds these into its own
 * `<Feature>Error.Read.*`. Mirrors `MealReadError`.
 */
sealed interface CuisineReadError {
    data object Unauthorized : CuisineReadError
    data object Unavailable  : CuisineReadError
}
