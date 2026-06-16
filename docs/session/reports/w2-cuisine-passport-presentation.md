# Report — `w2-cuisine-passport-presentation` (TERMINAL cuisine-passport task)

End-to-end cuisine passport: Firebase adapter (`CuisineReadPort`) + stamp-`Meal.cuisine`-at-publish +
stats passport grid + achievements `CuisineVariety` tie-in. The domain layer (`Cuisine`/`CuisineSlug`,
`CuisineReadPort`, `deriveCuisinePassport`, `Meal.cuisine`, `MealDraft.detectedDishSlug`) and the seed
(`cuisines`/`dishCuisineMap` + rules) were already on disk; this task built the adapter, wiring, and UI.

## Status: DONE — all verify commands green (Android + iOS).

## What was implemented

### 1. Firebase adapter — `CuisineReadPort` (in `:feature:ingredient`)
**Home justification:** placed alongside the ingredient catalog, NOT a new module. The cuisine read
path is a 1:1 mirror of the ingredient catalog (same Firestore snapshot pattern, same `LocalePort`-
derived `language: Flow<String>`, same app-lifetime `appScope`). A separate module would duplicate all
that wiring for two DTOs; reusing the catalog-reader feature is the layering-correct, lowest-surface
choice (the seed handoff explicitly says "mirror the ingredient catalog EXACTLY").
- `CuisineDto` (`slug`/`names`/`iconKey`) + `DishCuisineMapDto` (`dishSlug`/`cuisine`) — `@Serializable`,
  `ignoreUnknownKeys`-tolerant (drop the seeder's `updatedAt`/`modelLabel`).
- `CuisineFirestoreDataSource` — `cuisines.snapshots` (Flow) + `dishCuisineMap.document(id).get()` (one-shot).
- `CuisineMapper.toDomain(lang)` — `names[lang] ?: names["en"] ?: humanized()`; iconKey defaults to slug.
- `CuisineRepository : CuisineReadPort` — language-aware catalog `StateFlow` (re-maps on catalog OR
  language change), stable `LinkedHashMap` grid order; ONE `withContext(dispatchers.io)` on
  `loadDishCuisine`; vendor faults folded to `null`/empty (advisory — never crashes). No disk cache
  (14-row fixed set behind the warm app-scope listener; deliberate divergence from ingredients).
- `cuisineModule` (in `IngredientModule.kt`) binds `CuisineReadPort` over the SAME `appScope` as
  `ingredientModule`; registered in `shared` `appModules`. `CuisineModuleVerifyTest` added.

### 2. Publish stamping (`:feature:meal`) — advisory, non-blocking
- `UpdateMealDraftCommand.SetDetected` gained `dishSlug`; `UpdateMealDraftUseCase` writes
  `MealDraft.detectedDishSlug`; `ComposePlateViewModel.onPhotoCaptured` passes `r.value.dishSlug`;
  `MealDraftLocalStore` persists it (JSON DTO + both mappers).
- `FirebaseMealRepository` injects `CuisineReadPort`; resolves `cuisineSlug` ONCE before the fan-out
  (inside the existing publish `withContext` — the single IO boundary), wraps the call in
  `runCatching` so a lookup fault NEVER blocks publish (cuisine stays null). Writes `MealDto.cuisine`.
- `MealDto` gained `cuisine: String?` (default null); `MealMapper.toDomain` reads it (drop-on-malformed),
  `.from` writes it. Cross-feature ban preserved — meal goes through the `:core:domain` port only.

### 3. Stats passport grid (`:feature:stats`)
- Integrated into the existing `StatsViewModel`/`ObserveStatsUseCase` (MVI single source of truth — the
  passport lives in `StatsSnapshot.cuisinePassport`, derived in the same flow; no parallel state, no
  `withContext` in the VM). `ObserveStatsUseCase` injects `CuisineReadPort`, combines its catalog, and
  runs `deriveCuisinePassport` over the signed-in member's OWN confirmed meals (honest counting; reads
  `Meal.cuisine`, never re-derives). `null` until the catalog emits.
- `FrCuisinePassport` (feature `presentation/components/`) renders a `FlowRow` of `FrBadge` cells
  (collected vivid / locked dimmed) + a `collected / total` header. Reuses the pure `FrBadge` atom →
  NO new `Fr*` in `:core:designsystem`, so no catalog entry needed (feature-owned-Fr* carve-out).
  Rendered at the Stats tab-root, below the hero/today stripe.
- i18n: `PassportTitle`/`PassportProgressFormat` (`%1$d / %2$d`)/`PassportLockedLabel`/
  `PassportCollectedOnFormat` added to `StatsStringKey` + BOTH `values{,-es}/strings.xml`.

### 4. Badges tie-in (`:feature:achievements`)
Roadmap §2.2 says "tie to badges". Filled the `AchievementEvaluator.CuisineVariety` arm (was a
hard-coded `(0, target)` forward-hook) to count `mine.mapNotNull { it.meal.cuisine }.distinct().size`
— now functionally live against the freshly-stamped field. The catalog still ships NO `CuisineVariety`
row (spec §9/§15 — the achievements feature owns that decision; out of this task's scope), so both
existing pins stay green (`cuisineVariety_is_not_shipped`, and `cuisineVariety_always_locked` whose
fixtures carry no cuisine → still counts 0). No catalog/i18n/signals change — minimal, no test rewrites.

## Tests added
- `CuisineMapperTest` (6) — localized name, en fallback, humanized fallback, iconKey default, blank/overlong slug drop.
- `CuisineModuleVerifyTest` — Koin graph completeness for `cuisineModule`.
- `FirebaseMealRepositoryTest` +4 — stamp from detected dish, null when unmapped, null when lookup throws
  (publish still Ok), null when no dish detected. + `FakeCuisineReadPort`.
- `MealMapperTest` +2 — reads stamped cuisine slug, drops blank to null.
- `UpdateMealDraftUseCaseTest` — asserts `detectedDishSlug` carried.
- `StatsViewModelTest` +2 — passport collected/locked/progress; null when catalog empty. + `FakeCuisineReadPort`.
- `ObserveStatsUseCaseTest` — `FakeCuisineRead` threaded through all 6 ctor sites.

## Verify (all green)
```
./gradlew :feature:ingredient:testAndroidHostTest :feature:meal:testAndroidHostTest :feature:stats:testAndroidHostTest :feature:achievements:testAndroidHostTest :core:domain:testAndroidHostTest
  → BUILD SUCCESSFUL in 8s
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug
  → BUILD SUCCESSFUL in 8s
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
  → BUILD SUCCESSFUL in 47s
```
(`:feature:achievements` and `:shared`/`:androidApp` were run because they were touched/depend on the
changes; iOS link confirms the GitLive adapter + meal/stats wiring compile cross-platform.)

## Decisions
- Adapter home = `:feature:ingredient` (mirrors the catalog reader; no new module). Justified above.
- Passport integrated into `StatsViewModel`/`ObserveStatsUseCase` rather than a standalone VM/screen —
  it's a Stats SECTION per §2.2; keeps one MVI state and avoids a near-duplicate observe pipeline.
- Passport derives over the member's OWN confirmed window meals (uses historic window when the Historic
  tab loaded it, else the current week/month window) — always-available, no extra fetch.
- Achievements: live the evaluator arm but DON'T ship a catalog row (achievements feature owns §15).
- No analytics leaf added — §2.2 calls for none (no `cuisine_collected`/passport-viewed event listed).

## Files changed
- `feature/ingredient/.../data/firebase/{CuisineDto,CuisineFirestoreDataSource,CuisineMapper,CuisineRepository}.kt` (new)
- `feature/ingredient/.../di/IngredientModule.kt` (+`cuisineModule`)
- `feature/ingredient/src/androidHostTest/.../di/CuisineModuleVerifyTest.kt` (new)
- `feature/ingredient/src/commonTest/.../data/firebase/CuisineMapperTest.kt` (new)
- `shared/.../app/di/AppModule.kt` (register `cuisineModule`)
- `feature/meal/.../domain/usecase/{UpdateMealDraftCommand,UpdateMealDraftUseCase}.kt`
- `feature/meal/.../presentation/compose/ComposePlateViewModel.kt`
- `feature/meal/.../data/local/MealDraftLocalStore.kt`
- `feature/meal/.../data/firebase/{MealDto,MealMapper}.kt`
- `feature/meal/.../data/repository/FirebaseMealRepository.kt`
- `feature/meal/.../di/MealModule.kt`
- `feature/meal/src/androidHostTest/.../di/MealModuleVerifyTest.kt`
- `feature/meal/src/commonTest/.../{data/repository/FirebaseMealRepositoryTest,data/firebase/MealMapperTest,domain/usecase/UpdateMealDraftUseCaseTest}.kt`
- `feature/stats/.../domain/model/StatsSnapshot.kt`
- `feature/stats/.../domain/usecase/ObserveStatsUseCase.kt`
- `feature/stats/.../i18n/StatsStringKey.kt`
- `feature/stats/.../presentation/components/FrCuisinePassport.kt` (new)
- `feature/stats/.../presentation/stats/StatsScreen.kt`
- `feature/stats/src/commonMain/composeResources/values{,-es}/strings.xml`
- `feature/stats/src/androidHostTest/.../di/StatsModuleVerifyTest.kt`
- `feature/stats/src/commonTest/.../{presentation/stats/StatsViewModelTest,domain/usecase/ObserveStatsUseCaseTest}.kt`
- `feature/achievements/.../domain/AchievementEvaluator.kt`

## MANUAL deploy steps the USER must run (restated — until BOTH run, the passport has NO data)
1. `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`
   (the `cuisines`/`dishCuisineMap` public-read rules are at `firestore.rules` lines 255/259.)
2. `GOOGLE_APPLICATION_CREDENTIALS=/path/to/sa.json pnpm --dir functions seed:catalog`
   (seeds `cuisines` + `dishCuisineMap` alongside the ingredient collections.)
Also: old meals published before this ship carry `cuisine == null` and never collect — by design
(stamp-at-publish; no migration, pre-launch).

## Handoff (only if bingo / weekly-digest reuse the grid)
- The passport grid is `FrCuisinePassport(passport: CuisinePassport)` in `:feature:stats` — it is
  stats-feature-local (resolves `StatsStringKey` + reads domain `CuisinePassport`). A bingo/digest
  surface in another feature should NOT import it (cross-feature ban); reuse the pure `FrBadge` atom
  directly (it's the only design-system primitive here) and map your own domain → its props, exactly
  as `FrCuisinePassport` and `FrAchievementCard` both do.
- The cuisine-explorer badge is wired but NOT shipped: add a `CuisineVariety(target)` row to
  `AchievementCatalog.all` + its i18n keys to light it up; the evaluator arm already counts correctly.
