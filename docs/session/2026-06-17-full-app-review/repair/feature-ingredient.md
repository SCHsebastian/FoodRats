# feature-ingredient repair

## ingredient-01 (MEDIUM — IngredientRepository.kt)
Wrapped `datasource.loadDishMap(dishSlug)` in `runCatching { }.getOrNull()` so a Firestore throw (network error, missing collection) returns `emptyList()` instead of propagating and crashing the meal composer.
Test added: `suggestForDish_returns_empty_when_datasource_throws` in `IngredientRepositoryTest.kt`.

## ingredient-02 (MEDIUM — SelectIngredientsScreen.kt)
Built `detectedSlugs` set once via `remember(state.detected)` before the `CategoryOrder.forEach` loop. Category rows now filter `it.slug !in detectedSlugs`, so ingredients already shown in the Detected section are not duplicated in their category section.
No test needed (UI composable; behaviour locked by the deduplication logic which is straightforward).

## ingredient-03 (LOW arch — IngredientRepository.kt)
Moved `withContext(dispatchers.io) { cache.save(latest) }` out of `.onEach { }` into `scope.launch { withContext(io) { … } }`. The collecting flow is no longer suspended waiting for the cache write; it processes the next emission immediately. Added `import kotlinx.coroutines.launch`.

## ingredient-06 (LOW perf — IngredientRepository.kt)
Added `.distinctUntilChanged()` after `merge(cache, live)` (before `combine`). When the cache emits the same list that the live snapshot just emitted, the downstream combine skips the redundant recompute.

## ingredient-04 (SKIPPED — design)
`IngredientError` is dead (not plumbed into catalog flow or VM state). Wiring or deleting it is a design decision; skipped per instructions.

## Build risk
None. All edits are inside `feature/ingredient`. No public API changes. The `launch` call in `onEach` is fire-and-forget from `scope` (already the module's app-lifetime scope), which matches the intended lifetime. `distinctUntilChanged()` is a no-op unless the list value equality matches, which it does for `List<IngredientDto>` with data-class DTOs.
