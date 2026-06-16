# Handoff — `w2-cuisine-passport-domain` → `w2-cuisine-passport-presentation`

The cuisine DOMAIN is done & green (`:core:domain` + `:feature:meal` host tests). Stamp-at-publish was
chosen per roadmap §2.2. Below: the exact types, the port signature, the derivation signature, the
stamp-vs-derive decision + EXACTLY what you must implement, and the ingredient read pattern to mirror.

## Types (all in `:core:domain`, package `es.schsebastian.foodrats.core.domain.cuisine`)

```kotlin
@JvmInline value class CuisineSlug internal constructor(val value: String) {
    companion object {
        const val MAX_LEN = 64
        fun of(raw: String): Result<CuisineSlug, CuisineValueObjectError>   // trim; blank/too-long
    }
}

data class Cuisine(val slug: CuisineSlug, val displayName: String, val iconKey: String)

fun CuisineSlug.humanized(): String                                          // "middle_eastern" -> "Middle eastern"
fun cuisineNameResolver(catalog: Map<CuisineSlug, Cuisine>): (CuisineSlug) -> String

sealed interface CuisineValueObjectError { data object CuisineSlugBlank; data object CuisineSlugTooLong }
```

## Port (`:core:domain` — declared, NOT implemented here)

```kotlin
interface CuisineReadPort {
    fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>>     // live, language-resolved names
    suspend fun loadDishCuisine(dishSlug: String): CuisineSlug?  // one-shot; null if dish not in map
}
sealed interface CuisineReadError { data object Unauthorized; data object Unavailable }
```

## Pure derivation (`:core:domain` — read model)

```kotlin
data class CollectedCuisine(val cuisine: Cuisine, val collected: Boolean, val firstCollectedAt: Instant?)
data class CuisinePassport(val cells: List<CollectedCuisine>) {
    val collectedCount: Int   // cells.count { it.collected }
    val totalCount: Int       // cells.size
}

fun deriveCuisinePassport(
    catalog: Map<CuisineSlug, Cuisine>,   // from CuisineReadPort.observeCatalog() — defines grid cells + order
    confirmedMeals: List<Meal>,           // the user's published meals (cuisine stamped at publish)
): CuisinePassport
```
- One cell per catalog cuisine, in catalog iteration order (use a `LinkedHashMap` for stable grid order).
- Collected iff ≥1 meal has `meal.cuisine == slug`; `firstCollectedAt` = earliest `Meal.publishedAt`.
- Meals with `cuisine == null` or a slug absent from `catalog` contribute nothing.
- It reads `Meal.cuisine` directly — do NOT re-derive cuisine from ingredients or AI detections.

## DECISION: STAMP-AT-PUBLISH (roadmap §2.2 default). What YOU must implement.

The domain field exists; the **wiring does not** — this is the meaningful part of your task (no new module;
all touch points are inside `:feature:meal` + a new `data/firebase` adapter, plus the grid UI):

1. **Carry the dish slug onto the draft.** The classifier already yields it:
   `ClassifyDraftPlateUseCase` returns `DraftClassification(dishSlug, ingredients, version)` —
   `dishSlug` is the Food-101 `categoryName()` AND the `dishCuisineMap` doc id. Today it is **discarded**.
   - Add to `UpdateMealDraftCommand` (the `SetDetected` arm) a `dishSlug` parameter; have
     `UpdateMealDraftUseCase` write it to the new field `MealDraft.detectedDishSlug` (already added).
   - In `ComposePlateViewModel.onPhotoCaptured`, stamp `r.value.dishSlug` alongside the detected
     ingredients/version it already stamps.
   - Persist it through `MealDraftLocalStore` (add `detectedDishSlug` to the stored draft DTO + mappers,
     mirroring `classifierVersion`).
2. **Resolve + stamp at publish** in `FirebaseMealRepository` publish (~L193, the `MealDto` build loop):
   - Inject `CuisineReadPort`. Resolve once before the fan-out:
     `val cuisine = draft.detectedDishSlug?.let { cuisineRead.loadDishCuisine(it) }`.
   - Write it: add `cuisine: String?` to `MealDto` (default null, `@Serializable`), set `cuisine?.value`.
   - Keep the single `withContext(dispatchers.io)` boundary rule — `loadDishCuisine` is suspend; call it
     inside the existing publish `withContext` block (it's the data layer), not in a use case/VM.
3. **Read it back** in `MealMapper.toDomain`: `cuisine = dto.cuisine?.let { CuisineSlug.of(it).getOrNull() }`
   → `Meal.cuisine` (drop-on-read for malformed/unknown, as elsewhere). DTOs must stay
   `ignoreUnknownKeys`-tolerant (the seeder writes `updatedAt`/`modelLabel` your DTOs should NOT declare).
4. **The passport grid** (presentation): collect `CuisineReadPort.observeCatalog()` + the user's meals
   (via `MealReadPort` over the active crew range, or whatever stats already uses), run
   `deriveCuisinePassport`, render collected vivid / locked dimmed with "collected / total". Grid cell
   `Fr*` + catalog entries per the DoD (catalog-entry caveat: feature-owned `Fr*` may stay out of
   `:catalogApp` if it would force feature/Firebase deps — same stance as `FrMealCard` et al.).
5. **(Optional, in scope of 2.1↔2.2 tie-in but NOT this domain task)** fill the
   `AchievementCriterion.CuisineVariety` forward-hook in `feature/achievements/.../AchievementEvaluator.kt`
   (currently hard-coded `AchievementProgress(0, target)` "always locked"): count `mine.mapNotNull { it.meal.cuisine }.distinct().size`.

## Firebase adapter — mirror the ingredient catalog EXACTLY

Reference: `feature/ingredient/src/commonMain/.../data/firebase/IngredientFirestoreDataSource.kt` +
`IngredientRepository`. Build the cuisine adapter in the SAME shape (the seed handoff confirms the doc
shapes match this 1:1):

```kotlin
// catalog: snapshot listener (Flow) — like IngredientFirestoreDataSource.observeCatalog()
db.collection("cuisines").snapshots.map { snap -> snap.documents.map { it.data<CuisineDto>() } }

// dish -> cuisine: one-shot get(docId), nullable — like loadDishMap()
val doc = db.collection("dishCuisineMap").document(dishSlug).get()
if (doc.exists) doc.data<DishCuisineMapDto>() else null
```
- `@Serializable data class CuisineDto(slug, names: Map<String,String>, iconKey)` — NO `updatedAt`.
- `@Serializable data class DishCuisineMapDto(dishSlug, cuisine)` — NO `modelLabel`/`updatedAt`.
- `CuisineRepository` implements `CuisineReadPort`: bind over the app-lifetime `named("appScope")`
  `CoroutineScope` + the `LocalePort`-derived `language: Flow<String>` `StateFlow`, re-mapping
  `names[lang] ?: names["en"] ?: humanized()` exactly as `IngredientRepository` re-maps ingredient names.
  One `withContext(dispatchers.io)` per public method. Fold vendor exceptions → `CuisineReadError`.
- DI: a `cuisineModule` (commonMain) mirroring `ingredientModule`; register in `shared` `appModules`.
  Add `CuisineReadPort::class` to the feature/meal `*ModuleVerifyTest` `extraTypes` once
  `FirebaseMealRepository` depends on it.

## Closed cuisine slug set (14, locked by the seed integrity test)
`american, italian, french, mexican, spanish, greek, middle_eastern, japanese, chinese, korean, thai,
vietnamese, indian, british`. Every dish in `feature/meal-ai/food101_labels.txt` maps to exactly one of
these (no gaps), so for any classified dish `loadDishCuisine` returns non-null.

## Verify command for your task
`./gradlew :feature:meal:testAndroidHostTest` (+ your new module's host tests if you split the adapter
out) and `:core:domain:testAndroidHostTest` (Konsist + this domain's tests stay green).
