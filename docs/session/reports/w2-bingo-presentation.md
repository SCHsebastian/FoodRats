# Report — `w2-bingo-presentation` (TERMINAL ingredient-bingo task)

The "ingredient bingo" (roadmap §2.3): a Pokédex grid in Stats showing ALL catalog ingredients as
collected (the user posted a meal that CONFIRMS them) vs locked, with a `collected / total` progress
header. Mirrors the just-finished cuisine passport 1:1, but over INGREDIENTS instead of cuisines.

## Status: DONE — all verify commands green (Android host + assembleDebug).

No prior interrupted bingo work existed on disk (the modified stats/domain files in `git status` were
all from the preceding cuisine-passport task). Built from scratch, mirroring `CuisinePassport`.

## What was implemented

### 1. Pure domain derivation — `:core:domain/meal/IngredientBingo.kt` (new)
Exact structural mirror of `cuisine/CuisinePassport.kt`:
- `CollectedIngredient(ingredient, collected, firstCollectedAt)` — one Pokédex cell + the instant it
  was "caught" (earliest `Meal.publishedAt` among confirming meals); locked → `firstCollectedAt == null`.
- `IngredientBingo(cells)` with `collectedCount` / `totalCount` (the "142 / 226").
- `deriveIngredientBingo(catalog, meals)` — PURE (no I/O / Clock / Flow). An ingredient is *collected*
  when a meal CONFIRMS the slug via **`Meal.ingredients`** (user-confirmed list). AI
  `Meal.detectedIngredients` are **deliberately EXCLUDED** (§2.3 + the known merge-bug: an unconfirmed
  AI guess must never light a cell). Slugs absent from the catalog contribute nothing.
- **Performance (documented in the KDoc):** single pass over `meals` to build a `slug -> earliest
  instant` map, then map over `catalog` with O(1) lookups — **NOT O(meals × catalog)**. Fine for the
  ~226-ingredient catalog.

### 2. Stats integration (`:feature:stats`) — MVI single source of truth
- `StatsSnapshot` gained `ingredientBingo: IngredientBingo? = null` (alongside `cuisinePassport`).
- `ObserveStatsUseCase` **already injected `IngredientReadPort`** (used only for the name resolver). No
  new constructor param — its `observeCatalog()` flow is now ALSO consumed for the bingo. `compose(...)`
  now takes the raw `ingredientCatalog: Map<IngredientSlug, Ingredient>` (builds the name resolver
  internally) and derives the bingo over the signed-in member's OWN meals across the loaded window
  (the same member-own list the passport uses — computed once, reused for both). `null` until the
  ingredient catalog emits (empty catalog → no cells). No `withContext` in the use case/VM.
- `FrIngredientBingo` (feature `presentation/components/`, new) — `FlowRow` of `FrBadge` cells
  (collected vivid / locked dimmed, `FrIcons.Restaurant` glyph, `celebration` tint) + a `collected /
  total` header. Reuses the pure `FrBadge` atom → NO new `Fr*` in `:core:designsystem`, so no catalog
  entry needed (the feature-owned-`Fr*` carve-out — same as `FrCuisinePassport`/`FrMealCard`).
- Rendered in `StatsScreen` at the tab-root, immediately below the cuisine passport section (guarded by
  `totalCount > 0`).
- i18n: `BingoTitle` / `BingoProgressFormat` (`%1$d / %2$d`) / `BingoLockedLabel` / `BingoCollectedOnFormat`
  (`Caught %1$s`) added to `StatsStringKey` + BOTH `values/strings.xml` (en) and `values-es/strings.xml`
  (es: "Bingo de ingredientes" / "Capturado %1$s" / "Bloqueado"). Ingredient display names come from
  the localized catalog (already language-aware via `IngredientRepository`).

### 3. Badges tie-in — ALREADY LIVE, no change
`AchievementEvaluator.IngredientVariety` arm already counts `mine.flatMap { it.meal.ingredients }
.distinct().size` (confirmed-only, AI excluded — verified in the evaluator). The catalog already ships
`ingredients_25/50/100` rows (per the badges-domain handoff). The collection milestones are therefore
functionally wired against the same confirmed-ingredient signal the bingo uses — nothing to add.

### 4. Personal vs crew Pokédex (§2.3 decision)
§2.3 default is "both tabs (personal + crew)." Shipped the **personal** bingo only (over the member's
OWN meals) — this matches how the cuisine passport landed (personal), keeps one MVI state, and avoids
a second observe pipeline. A crew-shared tab is a clean follow-up: derive over ALL window meals (drop
the `author == accountId` filter) into a second `IngredientBingo` field + a tab toggle. Noted as
deferred, not blocked.

### 5. Category sections (§2.3 nice-to-have) — deferred
`Ingredient` carries `IngredientCategory`, so category grouping is feasible, but it would need 12 new
localized category-header strings (×2 locales) for marginal value over the flat catalog-ordered grid.
Rendered flat (scrolls fine for 226 cells inside the Stats `LazyColumn`); documented in the
`FrIngredientBingo` KDoc as a deferred enhancement.

## Tests added (all green)
- `core/domain/.../meal/IngredientBingoTest.kt` (7) — all-locked in catalog order; confirmed→collected
  with first-caught instant; not-confirmed stays locked; **unconfirmed-AI-detected NOT credited**;
  duplicate keeps earliest + counts once; catalog-absent slug ignored; multiple distinct collected.
- `StatsViewModelTest` +2 — bingo collected/locked/progress (incl. AI-detected basil staying locked);
  bingo null when ingredient catalog empty. (`makeVm` / `makeMealMine` gained ingredient params.)
- `ObserveStatsUseCaseTest` +2 — confirmed-only crediting (AI excluded); bingo null when catalog empty.
  (`FakeIngredientRead` made catalog-parameterizable; `makeMeal` gained ingredient params.)
- `StatsModuleVerifyTest` — UNCHANGED (no new Koin binding; `IngredientReadPort` already listed).

## Analytics
No leaf added — §2.3 lists no bingo event (no `ingredient_collected` / bingo-viewed). Per charter rule
9, analytics is added only when the spec calls for it.

## Verify (all green)
```
./gradlew :core:domain:testAndroidHostTest :feature:stats:testAndroidHostTest
  > Task :feature:stats:testAndroidHostTest
  > Task :core:domain:testAndroidHostTest
  BUILD SUCCESSFUL in 21s
```
```
./gradlew :feature:achievements:testAndroidHostTest :androidApp:assembleDebug
  > Task :androidApp:assembleDebug
  BUILD SUCCESSFUL in 5s
```
(`:feature:achievements` run because the badges tie-in lives there — confirmed it still compiles/passes
unchanged; `:androidApp:assembleDebug` confirms the new stats UI + domain wire through the full graph.)
Explicit re-run of `*IngredientBingoTest`, `*StatsViewModelTest`, `*ObserveStatsUseCaseTest` with
`--rerun-tasks`: BUILD SUCCESSFUL.

## Decisions
- Domain derivation home = `:core:domain/meal/` (mirrors `CuisinePassport` in `:core:domain/cuisine/`).
  Did NOT generalize a shared "Collection<T>" shape — the two derivations read different `Meal` fields
  (`cuisine` single-stamped slug vs `ingredients` list) and forcing a generic would churn both with no
  payoff. Two small parallel pure functions is the lower-surface choice.
- Personal bingo only (matches the passport); crew-shared tab deferred (§2.3 default is both — noted).
- Flat grid (no category sections) — deferred to avoid 12×2 new strings for marginal value.
- Confirmed `Meal.ingredients` ONLY for collection (AI `detectedIngredients` excluded) — §2.3 + the
  honesty rule; a dedicated test locks this.
- No new `Fr*` in `:core:designsystem` → no catalog entry (reuses `FrBadge`; feature-owned-Fr* carve-out).

## Files changed
- `core/domain/.../meal/IngredientBingo.kt` (new)
- `core/domain/src/commonTest/.../meal/IngredientBingoTest.kt` (new)
- `feature/stats/.../domain/model/StatsSnapshot.kt`
- `feature/stats/.../domain/usecase/ObserveStatsUseCase.kt`
- `feature/stats/.../i18n/StatsStringKey.kt`
- `feature/stats/.../presentation/components/FrIngredientBingo.kt` (new)
- `feature/stats/.../presentation/stats/StatsScreen.kt`
- `feature/stats/src/commonMain/composeResources/values{,-es}/strings.xml`
- `feature/stats/src/commonTest/.../{presentation/stats/StatsViewModelTest,domain/usecase/ObserveStatsUseCaseTest}.kt`

## MANUAL deploy steps the USER must run (restated — until BOTH run, the bingo shows ALL cells LOCKED)
The bingo needs the ingredient catalog seeded (it reads the FULL catalog to draw locked cells):
1. `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
   (the `ingredients` public-read rule must be live — it was deployed alongside the cuisine rules per
   the cuisine-passport-seed report; re-confirm.)
2. `GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm --dir functions seed:catalog`
   (seeds the 226-ingredient `ingredients` collection.)
Also: ingredients are credited at-publish from confirmed `Meal.ingredients`; old meals without
confirmed ingredients never collect — by design (pre-launch, no migration).

## Handoff (only if weekly-digest reuses the grid)
`FrIngredientBingo(bingo: IngredientBingo)` is stats-feature-local (resolves `StatsStringKey`, reads
domain `IngredientBingo`). A digest/other-feature surface must NOT import it (cross-feature ban) — reuse
the pure `FrBadge` atom directly and map your own domain → its props, exactly as `FrIngredientBingo` /
`FrCuisinePassport` / `FrAchievementCard` all do. The pure `deriveIngredientBingo(catalog, meals)` in
`:core:domain` IS reusable from any feature (it's a domain fn) if a digest wants the same numbers.
