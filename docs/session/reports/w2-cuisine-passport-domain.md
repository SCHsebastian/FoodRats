# Report — `w2-cuisine-passport-domain`

The DOMAIN layer for the cuisine passport: `Cuisine`/`CuisineSlug` value objects, a `CuisineReadPort`
(catalog + dish→cuisine map), the pure `deriveCuisinePassport` derivation, and the stamped `cuisine`
field on the meal domain models. DOMAIN ONLY — the Firebase catalog adapter + the passport grid UI is
`w2-cuisine-passport-presentation`.

## Decision: STAMP-AT-PUBLISH (per roadmap §2.2) — but the dish slug is NOT persisted today

Roadmap §2.2 default is explicit: "stamp `cuisine` on `Meal` at publish vs. derive at read. **Default:
stamp at publish (stable, survives map changes).**" The seed handoff agrees. So this task adds a
`cuisine: CuisineSlug?` field to `Meal` and `MealDraft` and the derivation reads `Meal.cuisine` directly.

**The gap (flagged in the brief):** the `dishCuisineMap` key is the Food-101 **dish slug** (the classifier
`categoryName()`, e.g. `pizza`). That slug is produced transiently by `ClassifyDraftPlateUseCase`
(`DraftClassification.dishSlug`) and then **discarded** — the `MealDraft`/`Meal` persist only the
human-readable `dish: DishName` (e.g. "Pizza"), `detectedIngredients`, and `classifierVersion`. There is
**no current path** from the detected dish slug to a stamped cuisine. So stamp-at-publish requires the
presentation/data task to thread the dish slug from classify → draft → publish, then resolve it via the
port. I added the carrier field `MealDraft.detectedDishSlug` and the `Meal.cuisine`/`MealDraft.cuisine`
fields, and documented the exact write contract in the handoff. **A separate data task is NOT needed** —
the wiring is squarely inside `w2-cuisine-passport-presentation`'s meal/data touch points (no new module),
but it is meaningful work, not a one-liner.

The derivation is robust to the field being `null` (meal published before stamping shipped, or a dish not
in the map): such meals simply contribute nothing. No migration (pre-launch).

## What was added (all in `:core:domain`, plus two meal-model fields)

### `core/domain/.../cuisine/Cuisine.kt`
- `@JvmInline value class CuisineSlug internal constructor(val value: String)` with a validating
  `CuisineSlug.of(raw): Result<CuisineSlug, CuisineValueObjectError>` (trim; blank → `CuisineSlugBlank`;
  > `MAX_LEN`=64 → `CuisineSlugTooLong`). Mirrors `IngredientSlug` exactly.
- `data class Cuisine(slug: CuisineSlug, displayName: String, iconKey: String)` — leaner than `Ingredient`
  (no category/aliases; cuisine = slug + localized name + iconKey, matching `cuisines/{slug}`).
- `CuisineSlug.humanized()` + `cuisineNameResolver(catalog): (CuisineSlug) -> String` — fallback display
  for slugs absent from the localized catalog. Mirrors `ingredientNameResolver`.
- `sealed interface CuisineValueObjectError { CuisineSlugBlank; CuisineSlugTooLong }` — kept cuisine-local
  (not folded into `MealValueObjectError`) so the cuisine concept is self-contained.

### `core/domain/.../cuisine/CuisineReadPort.kt`
- `interface CuisineReadPort`
  - `fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>>` — live, language-resolved (mirror
    `IngredientReadPort.observeCatalog`).
  - `suspend fun loadDishCuisine(dishSlug: String): CuisineSlug?` — one-shot dish→cuisine lookup; `null`
    when the dish isn't in `dishCuisineMap` (mirror `IngredientReadPort.suggestForDish` shape).
- `sealed interface CuisineReadError { Unauthorized; Unavailable }` — for the adapter to fold its vendor
  exceptions into (mirror `MealReadError`).

### `core/domain/.../cuisine/CuisinePassport.kt`
- `data class CollectedCuisine(cuisine: Cuisine, collected: Boolean, firstCollectedAt: Instant?)`.
- `data class CuisinePassport(cells: List<CollectedCuisine>)` with `collectedCount` / `totalCount`.
- `fun deriveCuisinePassport(catalog: Map<CuisineSlug, Cuisine>, confirmedMeals: List<Meal>): CuisinePassport`
  — PURE (no I/O/Clock/Flow). One cell per catalog cuisine, in catalog iteration order. A cuisine is
  *collected* iff ≥1 confirmed meal carries its slug; `firstCollectedAt` = earliest `Meal.publishedAt` for
  that slug. Meals with `cuisine == null` and slugs not in `catalog` contribute nothing.

### Meal model fields (domain only — write contract is for presentation/data task)
- `core/domain/.../meal/Meal.kt`: `val cuisine: CuisineSlug? = null` (stamped at publish).
- `feature/meal/.../domain/model/MealDraft.kt`: `val detectedDishSlug: String? = null` (carries the
  classifier dish slug to publish) and `val cuisine: CuisineSlug? = null`. Both default-`null` so all
  existing `Meal`/`MealDraft` construction sites compile unchanged.

## "Confirmed only" honesty (§2.2 ⚠️)
Cuisine is stamped at publish from the **detected dish**, not from the meal's ingredient list, so the
detected-vs-confirmed-ingredients bug does not apply: a published `Meal.cuisine` IS the confirmed signal.
The derivation deliberately reads `Meal.cuisine` and never re-derives from `detectedIngredients`. The
caller must still pass the user's own published meals (the same set stats/achievements score against).

## Tests (all `commonTest`, run on every target via `testAndroidHostTest`)
- `CuisineSlugTest` (5) — valid/trim/blank/too-long/at-max.
- `CuisineResolutionTest` (3) — humanized, resolver hit, resolver fallback.
- `CuisinePassportTest` (7) — empty/all-locked + order; collected + first-collected instant; not-collected
  stays locked; duplicate keeps earliest + counts once; null-cuisine ignored; unknown-slug-not-in-catalog
  ignored; multiple distinct collected.

## Verification

```
$ ./gradlew :core:domain:testAndroidHostTest :feature:meal:testAndroidHostTest
> Task :feature:meal:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 18s
102 actionable tasks: 27 executed, 1 from cache, 74 up-to-date
```

Per-class result XML counts: `CuisinePassportTest` tests=7 failures=0 errors=0; `CuisineResolutionTest`
tests=3 failures=0; `CuisineSlugTest` tests=5 failures=0; `KonsistRulesTest` tests=1 failures=0 (the new
`cuisine/` package imports only kotlin stdlib + kotlinx — no Firebase/Android/Compose).

## Manual user steps (deploy — also surfaced by the seed task; add to human.md, deduplicated)
1. `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
2. `GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm --dir functions seed:catalog`
   (seeds `cuisines` + `dishCuisineMap`). Until both run, the passport has no data.

## Suggested next
`w2-cuisine-passport-presentation`: implement the Firebase adapter (`CuisineFirestoreDataSource` +
`CuisineRepository` binding `CuisineReadPort`), wire stamp-at-publish (thread `detectedDishSlug` →
`loadDishCuisine` → `Meal.cuisine` + `MealDto`), build the passport grid, and optionally fill the
`AchievementCriterion.CuisineVariety` forward-hook (currently always-locked).
