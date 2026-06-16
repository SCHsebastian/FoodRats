# Handoff — `w2-badges-presentation`

For cuisine-passport, ingredient-bingo, and weekly-digest — they plug into THIS achievements
taxonomy + reuse the badge UI. Presentation layer is DONE and green (Android `assembleDebug` +
`catalogApp` + full host suites). Spec §8/§12/§13/§15.

## Reusable design-system atom — `FrBadge` (`:core:designsystem/atoms/FrBadge.kt`)

Pure, domain-free badge atom. Reuse it directly for any badge-grid surface:

```kotlin
@Composable
fun FrBadge(
    icon: ImageVector,
    title: String,
    earned: Boolean,
    progressFraction: Float,          // 0f..1f; sweeps a ring when !earned
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    tier: FrBadgeTier = FrBadgeTier.None,   // None/Bronze/Silver/Gold — presentation enum
    caption: String? = null,          // "30 / 50" or "Earned 2026-05-04"
    contentDescription: String? = null,
)
```
- It is **domain-free** — never pass it an `Achievement`. Map your domain → these props in your OWN
  feature (the `FrMealCard` rule). The mapper for achievements is
  `feature/achievements/.../presentation/components/FrAchievementCard.kt` — copy its shape.
- Catalog entry already exists (`atom.badge` in `stories/AtomStories.kt`).
- New badge glyphs go in `FrIcons.kt` via the `materialIcon { materialPath { … } }` DSL (iOS-safe).
  Added so far: `Restaurant, Trophy, Eco, Sun, Moon, Public` (+ existing `Crown, Flame, Group, Star`).

## Reusable display model + visual mappers (`:feature:achievements`)

- The display unit is `AchievementStatus(achievement, progress, unlockedAtEpochMs)` (domain). Earned =
  `unlockedAtEpochMs != null`. A new criterion = a new `AchievementCriterion` leaf + a `when` arm in
  `AchievementEvaluator` + catalog rows + i18n keys — the engine/persist/celebrate path is untouched.
- `presentation/components/AchievementVisuals.kt`: `AchievementIcon.toVector()`,
  `AchievementTier?.toBadgeTier()`, `AchievementIcon.tint()` (streak families → `streakHot`, else
  `celebration`, both from `LocalFrSemanticColors`). **Add a `when` arm** when you add an
  `AchievementIcon` enum value (e.g. cuisine-passport's `Globe` → `FrIcons.Public` is already mapped).
- `presentation/components/FrAchievementCard.kt`: the domain→`FrBadge` wrapper (resolves i18n). Reuse
  it on any achievement-style surface; it already handles earned/locked caption.

## How the pipeline runs (so new criteria fit without re-plumbing)

`ObserveAchievementsUseCase` (pure orchestration, no `withContext`):
`combine(ActiveCrewProvider, SessionProvider) → flatMapLatest { combine(MealReadPort.observeRange(crew, today-365, today), AchievementProgressPort.observeUnlocks) } → debounce(400ms) → AchievementSignalsBuilder.build → AchievementEvaluator.evaluate(AchievementCatalog.all, signals) → AchievementReconciler.reconcile(persisted) → AchievementsSnapshot`.
- Cuisine-passport: add `distinctCuisines` to `AchievementSignals` + populate it in
  `AchievementSignalsBuilder.build`; replace the placeholder `CuisineVariety` arm in the evaluator;
  add catalog rows. No ViewModel/screen change needed — they render via `FrAchievementCard`.
- Ingredient-bingo: declare an `IngredientBingo` criterion leaf + evaluate it; render its OWN surface
  reusing `FrBadge`/`AchievementStatus`.

## Unlock celebration + analytics (the side-effect contract)

- `AchievementsViewModel.persistAndCelebrate` is the ONLY side-effecting step: it calls
  `recordUnlocks` ONCE for newly-met ids and, **only on `Ok`**, fires
  `AnalyticsEvent.AchievementUnlocked(id)` + `emit(AchievementsEffect.Unlocked(titleKey))`. Never
  before persistence, never in a use case. New unlock-driven analytics follows the same shape.
- `AnalyticsEvent.AchievementUnlocked` (`:core:domain/analytics/AnalyticsEvent.kt`) = `achievement_unlocked`,
  param `achievement_id` (catalog slug; NO PII). It's in `AnalyticsTaxonomyTest` + `TRACKING_PLAN.md`.

## Weekly-digest

The digest can surface "N new badges this week" — read it off the persisted unlock timestamps
(`AchievementProgressPort.observeUnlocks` → epoch-ms; filter by week). If the digest is a Cloud
Function, it reads `accounts/{uid}/achievements/{id}.unlockedAtEpochMs` directly (one doc per unlocked
achievement; absence = locked). The Kotlin taxonomy is NOT visible server-side; the digest names a
badge by its slug + a server-side copy table (or just the count).

## Koin / route facts you may need

- `Route.Achievements : Route.Protected` (in `shared/.../navigation/Route.kt`), reached from the
  Profile "Badges" row (`ProfileScreen.onOpenAchievements`). Route name → `achievements` (screen-view
  auto-tracked).
- `achievementsModule` binds the explicit `viewModel { AchievementsViewModel(... analytics = get()) }`
  (NOT `viewModelOf`). A new VM that injects `AnalyticsPort` must do the same + add `AnalyticsPort::class`
  to its `*ModuleVerifyTest.extraTypes`.

## MANUAL step the USER must run (still pending, carried from `w2-badges-data`)

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```
The `accounts/{uid}/achievements/{id}` owner-only rule is in `firestore.rules`. **Until deployed,
`observeUnlocks`/`recordUnlocks` get PERMISSION_DENIED** → the achievements screen shows the read
error and persists no unlocks.
