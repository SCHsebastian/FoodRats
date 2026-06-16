# Report — `w2-cuisine-passport-seed`

Seed/functions layer for the cuisine passport feature (roadmap §2.2). Done & verified green.

## What was built

Mirrors the proven ingredient-catalog seed pattern exactly (JSON + `seed-catalog.ts` uploader
+ vitest referential-integrity tests + `firestore.rules` public-read block).

### Files created
- `functions/seed/cuisines.json` — closed catalog of **14** cuisines with en/es localized display
  names + `iconKey` (each `iconKey == slug`), mirroring `ingredients.json`'s localized shape
  (cuisines have no `category`/`aliases` — those are ingredient-specific).
  Slugs: `american, italian, french, mexican, spanish, greek, middle_eastern, japanese,
  chinese, korean, thai, vietnamese, indian, british`.
- `functions/seed/dish-cuisine-map.json` — all **101** Food-101 dish slugs → one **primary**
  cuisine each (§2.2 mirrors `dishIngredientMap`, which is 1-doc-per-dish; §2.2 does not call for
  multiple cuisines per dish, so one primary cuisine, like the dish-ingredient map is one row per
  dish). Shape per row: `{ dishSlug, modelLabel, cuisine }` (mirrors the `{ dishSlug, modelLabel,
  defaultIngredients }` shape; `modelLabel == dishSlug`). Keys are **set-equal** to
  `feature/meal-ai/food101_labels.txt`.
- `functions/__tests__/seedCuisines.test.ts` — 8 vitest integrity tests (mirrors `seed.test.ts`).

### Files edited
- `functions/scripts/seed-catalog.ts` — extended the existing uploader to also upload the
  `cuisines` and `dishCuisineMap` collections (two more idempotent batched writes; same
  `{ merge: true }` + `updatedAt` style). `assertIntegrity` now also fails fast on duplicate
  cuisine slugs / dishes referencing an unknown cuisine. The existing `seed:catalog` pnpm script
  now covers all four collections — **no new script added** (one command seeds everything, like the
  ingredient catalog).
- `firestore.rules` — added `match /cuisines/{slug}` and `match /dishCuisineMap/{dishSlug}` blocks,
  both `allow read: if true; allow write: if false;` — identical contract to `ingredients` /
  `dishIngredientMap` (public read, no client write; seeded only via the admin SDK which bypasses
  rules).

## Decisions

- **One primary cuisine per dish** (not multi-cuisine). §2.2 explicitly says "mirroring
  `dishIngredientMap`" and "derive a cuisine per dish"; the dish-ingredient map is one document per
  dish, so the dish-cuisine map is too. A dish → exactly one cuisine slug.
- **Dropped `mediterranean` from the initial catalog.** No Food-101 dish maps to a generic
  "Mediterranean" once `greek` / `middle_eastern` / `italian` / `spanish` exist, and an unused
  cuisine would (a) fail the no-orphan integrity test and (b) show as a permanently-locked passport
  cell the user can never collect. Closed set = exactly the 14 cuisines that 101 dishes actually use.
- **Cuisine doc shape omits `category` and `aliases`.** Those fields are ingredient-specific
  (food taxonomy / search aliases); a cuisine only needs slug + localized names + iconKey.
- **No npm/pnpm script added** — the existing `seed:catalog` now uploads all four collections,
  matching how the ingredient catalog has a single `seed:catalog` entry.
- **Did not touch `firestore-tests/`.** That harness has NO coverage of reference-data
  (`ingredients` / `dishIngredientMap`) reads today — confirmed by `grep`. Adding cuisine coverage
  there would be net-new scope inconsistent with the ingredient precedent; the vitest seed test is
  the integrity surface, exactly as for ingredients.

## Cuisine distribution (sanity)

```
36 american   16 french   15 italian    9 japanese   7 mexican   5 chinese
 3 middle_eastern   2 spanish   2 indian   2 british   1 vietnamese   1 thai   1 korean   1 greek
```

## Verify

### `pnpm --dir functions test` — last 3 lines
```
 Test Files  9 passed (9)
      Tests  88 passed (88)
   Duration  705ms (...)
```
(includes `__tests__/seedCuisines.test.ts (8 tests)` + the pre-existing `seed.test.ts (9 tests)`,
all green. The `deleteAccount` stderr is an expected logged error from a negative-path test, not a
failure — that file shows `11 passed`.)

### `pnpm --dir functions build` (`tsc`) — last lines
```
$ tsc
EXIT=0
```
Clean compile (the new JSON imports + types in `seed-catalog.ts` resolve under `resolveJsonModule`).

### jq referential-integrity cross-check
```
=== dish-cuisine keys vs food101_labels.txt ===  → SET-EQUAL OK
=== cuisines used vs catalog ===                 → (no diff)
=== orphan check (catalog cuisines not used) ===  → (none)
counts: 101 dish-cuisine rows, 14 cuisines
```

### `firestore-tests`
Not run — the harness has no catalog/reference-read coverage to extend (the ingredient catalog has
none either). The rules change is the standard public-read-no-write block mirrored verbatim from
`ingredients`.

## Blockers
None.

## Manual steps the user must run (deploy)
1. **Deploy rules:** `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
2. **Run the seed (needs a service-account key):**
   `GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm --dir functions seed:catalog`
   — this now writes `ingredients`, `dishIngredientMap`, `cuisines`, AND `dishCuisineMap`.
   Until both run, the `cuisines`/`dishCuisineMap` collections are empty and the passport has no data.
