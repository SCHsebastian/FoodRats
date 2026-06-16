# Handoff — `w2-cuisine-passport-seed` → `w2-cuisine-passport-domain` (+ `-presentation`)

The seed/functions layer is done. Two new public-read Firestore collections now exist and are
seeded by `functions/scripts/seed-catalog.ts` (the SAME script + `seed:catalog` command that seeds
ingredients). Rules: both public-read, no client write — mirror of `ingredients`/`dishIngredientMap`.

## Firestore collections + doc shapes

### `cuisines/{slug}` — the cuisine catalog (the passport grid's cells)
```json
{
  "slug": "italian",
  "names": { "en": "Italian", "es": "Italiana" },
  "iconKey": "italian",          // always == slug
  "updatedAt": <Timestamp>       // stamped by the seeder; ignore on read
}
```
Note: NO `category`/`aliases` (those are ingredient-only). A cuisine = slug + localized names + iconKey.

### `dishCuisineMap/{dishSlug}` — Food-101 dish → primary cuisine
```json
{
  "dishSlug": "pizza",
  "modelLabel": "pizza",         // always == dishSlug (the MediaPipe categoryName())
  "cuisine": "italian",          // exactly one cuisine slug; ∈ the cuisines catalog
  "updatedAt": <Timestamp>
}
```
Doc id = the dish slug. **One cuisine per dish** (1:1, like dishIngredientMap is 1-row-per-dish).
Keys are set-equal to `feature/meal-ai/food101_labels.txt` (all 101 dishes covered, no gaps).

## The closed cuisine slug set (14)
```
american, italian, french, mexican, spanish, greek, middle_eastern,
japanese, chinese, korean, thai, vietnamese, indian, british
```
Every one is used by ≥1 dish (no orphans — locked by the integrity test). The passport grid has
exactly these 14 cells.

## How the client domain task should READ these (mirror the ingredient catalog EXACTLY)

The ingredient catalog read path is the proven template — copy it for cuisines. Reference:
`feature/ingredient/src/commonMain/.../data/firebase/IngredientFirestoreDataSource.kt`:

```kotlin
// catalog: snapshot listener (Flow) — same as observeCatalog()
db.collection("cuisines").snapshots.map { snap ->
    snap.documents.map { it.data<CuisineDto>() }
}

// dish → cuisine: one-shot get(docId), nullable — same as loadDishMap()
val doc = db.collection("dishCuisineMap").document(dishSlug).get()
if (doc.exists) doc.data<DishCuisineMapDto>() else null
```

Concretely for `w2-cuisine-passport-domain`:
- Declare a `CuisineReadPort` in `:core:domain` (roadmap §2.2 offers "extend `IngredientReadPort`
  or new `CuisineReadPort`" — a new port is cleaner; the cuisine catalog is a distinct read model).
  Expose `observeCatalog(): Flow<List<Cuisine>>` + a dish→cuisine lookup
  (`loadDishCuisine(dishSlug): Cuisine?` or `cuisineFor(dishSlug)`).
- `Cuisine` VO: `slug: CuisineSlug` (`@JvmInline value class`) + localized name resolved by active
  language (mirror how `IngredientRepository` re-maps names off a `language: Flow<String>` — see
  the meal-ai memory note). Errors as sealed interfaces (`CuisineReadError`), not enums.
- Adapter: `CuisineFirestoreDataSource` + `CuisineRepository` in the feature's `data/firebase/`,
  DTOs (`CuisineDto`, `DishCuisineMapDto`) with `@Serializable` + `ignoreUnknownKeys`-tolerant
  reads (the seeder writes an extra `updatedAt` field DTOs should NOT declare). One `withContext`
  per public repo method.
- **Stamp-at-publish (the §2.2 default):** resolve the dish's cuisine via the port at publish and
  stamp `cuisine` onto the `Meal` (stable; survives future map changes). Add `cuisine` to
  `Meal`/`MealDraft`/`MealDto` like `ingredients` was added.
- **Honest counting (§2.2 ⚠️):** the passport must count cuisines from CONFIRMED meals only — the
  same confirmed-vs-AI-detected rule stats already uses. Don't count unconfirmed AI detections.

## Module/DI wiring
Mirror `ingredientModule` (commonMain) — bind `CuisineReadPort` → `CuisineRepository` over the
app-lifetime `named("appScope")` scope + the `LocalePort`-derived language `StateFlow`. Register in
`shared` `appModules`. The `presentation` task builds the passport grid (collected vs locked) over
the port.

## Manual deploy steps the user MUST run (also in the report; add to human.md)
1. `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
2. `GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm --dir functions seed:catalog`
   (now seeds `cuisines` + `dishCuisineMap` alongside the ingredient collections)
Until BOTH run, `cuisines`/`dishCuisineMap` are empty and the passport has no data.
