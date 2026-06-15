# Report — `w2-badges-presentation`

The achievements UI: an `FrBadge` design-system atom (+ catalog entry + UI test), the
`AchievementsViewModel`/`Screen` (badge grid, detail sheet, unlock celebration), the orchestrating
`ObserveAchievementsUseCase` + feature-local `AchievementSignalsBuilder`, the `achievement_unlocked`
analytics leaf, a `Route.Achievements` + Profile entry point, and i18n. Built on top of the
already-DONE domain (`w2-badges-domain`) + data (`w2-badges-data`) layers. Spec §7/§8/§12/§13/§14.

## What was built

### Analytics (`:core:domain`)
- `AnalyticsEvent.AchievementUnlocked(achievementId)` → `achievement_unlocked`, param `achievement_id`
  (catalog slug, no PII). Added a `// ── achievements ──` section.
- `AnalyticsTaxonomyTest` extended with `AchievementUnlocked("first_plate")`.
- `docs/analytics/TRACKING_PLAN.md` gained the row.

### `FrBadge` atom (`:core:designsystem`)
- `atoms/FrBadge.kt` — pure, domain-free atom: tinted icon disc + title + optional caption slot;
  `earned` (vivid disc, full ring) vs locked (dimmed disc + animated progress-sweep ring); presentation
  enum `FrBadgeTier { None, Bronze, Silver, Gold }` styles the earned-state ring. Takes
  `ImageVector` + primitives only — NO domain imports.
- `atoms/FrIcons.kt` — vendored 6 new glyphs via the `materialIcon { materialPath { … } }` DSL
  (iOS-safe, no `material-icons-extended`): `Restaurant`, `Trophy`, `Eco`, `Sun`, `Moon`, `Public`.
- Catalog: `stories/AtomStories.kt` gained `CatalogEntry("atom.badge", …)` + a `BadgeStory()` with
  three scenes (earned-vs-locked split, tier row, locked progress sweep). REQUIRED by the
  catalog-per-`Fr*` rule.
- UI test: `androidHostTest/.../FrBadgeTest.kt` (3 tests, Robolectric v2 harness, mirrors `FrAvatarTest`).

### Feature presentation (`:feature:achievements`)
- `domain/AchievementSignalsBuilder.kt` — **feature-local, pure** re-implementation of personal /
  crew streak + best-cook (min-3-plates) from the crew meal window. Does NOT depend on `:feature:stats`.
- `domain/usecase/ObserveAchievementsUseCase.kt` — pure orchestration (no `withContext`):
  `combine(ActiveCrewProvider, SessionProvider) → flatMapLatest { combine(MealReadPort.observeRange(crew, today-365, today), AchievementProgressPort.observeUnlocks) } → debounce(400ms) → build signals → evaluate → reconcile → AchievementsSnapshot(accountId, statuses)`.
  Maps `MealReadError` + `AchievementProgressError` → `AchievementError.Read.*`.
- `presentation/AchievementsContract.kt` — `AchievementsState`/`Intent`/`Effect.Unlocked` per spec §8.1.
- `presentation/AchievementsViewModel.kt` — MVI single source of truth (no parallel `MutableStateFlow`).
  `persistAndCelebrate` writes newly-met unlocks ONCE; **only on `Ok`** does it `track(AchievementUnlocked)`
  + `emit(Unlocked)` — never before persistence, never in a use case. `analytics: AnalyticsPort =
  NoopAnalyticsTracker` default.
- `presentation/AchievementsScreen.kt` — `LazyVerticalGrid` of `FrBadge`s split into Earned/Locked
  sections, a tapped-badge `ModalBottomSheet` detail (description + earned-on date / progress + Locked
  label), an error banner, an empty state, and the unlock-celebration overlay (`FrConfirmDialog`,
  driven by the `Unlocked` effect).
- `presentation/components/` — `FrAchievementCard` (domain-aware wrapper mapping `AchievementStatus`
  → `FrBadge` props; lives in the feature, not the DS, per the `FrMealCard` rule), `AchievementVisuals`
  (`AchievementIcon → FrIcons`, `AchievementTier → FrBadgeTier`, meaning tint via
  `LocalFrSemanticColors` — `streakHot` for streaks, `celebration` otherwise), `EpochDayFormat`
  (epoch-ms → ISO `yyyy-MM-dd`; ISO chosen because cross-platform CLDR month names aren't uniformly
  available in commonMain).

### i18n
- `AchievementStringKey` + both `values{,-es}/strings.xml`: added `CelebrationTitle`,
  `DetailLockedLabel`, `DetailCloseCta`, `EmptySubtext` (the per-row + chrome + error keys were already
  authored by the domain task).
- `AuthStringKey` + both auth `strings.xml`: `ProfileAchievementsSection/Row/Subtitle` (the Profile
  entry-point row).

### Navigation + Koin
- `shared/.../app/navigation/Route.kt` — `Route.Achievements : Protected` + added to the exhaustive
  `requiresSession()` `when`. Maps cleanly to `achievements` via the existing route→snake_case
  `TrackScreenViews` mapping (screen-view auto-tracked).
- `NavGraph.kt` — `composable<Route.Achievements> { AchievementsScreen(onBack = …) }`; `Profile`
  composable now passes `onOpenAchievements = { navigate(Route.Achievements) }`.
- `ProfileScreen` — new `onOpenAchievements` param + a "Badges" settings row.
- `di/AchievementsModule.kt` — added `singleOf(::AchievementSignalsBuilder)`, `factoryOf(::ObserveAchievementsUseCase)`,
  and the **explicit** `viewModel { AchievementsViewModel(observeAchievements = get(), progress = get(),
  clock = get(), analytics = get()) }` (NOT `viewModelOf`, so the `AnalyticsPort` default isn't
  short-circuited). The module is already in `appModules` + `shared/build.gradle.kts`.
- `AchievementsModuleVerifyTest` extraTypes += `MealReadPort`, `ActiveCrewProvider`, `SessionProvider`,
  `Clock`, `TimeZone`, `AnalyticsPort`.
  - `:androidApp` did NOT need a `:feature:achievements` dep — the NavGraph lives in `:shared`, which
    already depends on it; `:androidApp` reaches the screen transitively.

### Tests
- `AchievementsViewModelTest` (commonTest, 3 cases): earned (persisted) vs locked render; a newly-met
  badge persists + tracks `achievement_unlocked` + emits `Unlocked`; a **failed** persist records the
  attempt but fires NO analytics/effect. Uses `RecordingAnalyticsTracker` + a fake
  `AchievementProgressPort`/`MealReadPort`; `advanceUntilIdle()` flushes the 400ms debounce.
- `FrBadgeTest` (designsystem androidHostTest, 3 cases).

## Verification (all green)

```
./gradlew :feature:achievements:testAndroidHostTest   → BUILD SUCCESSFUL  (47 tests)
./gradlew :core:designsystem:testAndroidHostTest      → BUILD SUCCESSFUL  (48 tests)
./gradlew :core:domain:testAndroidHostTest            → BUILD SUCCESSFUL  (98 tests)
./gradlew :shared:testAndroidHostTest                 → BUILD SUCCESSFUL  (31 tests)
./gradlew :androidApp:assembleDebug                   → BUILD SUCCESSFUL
./gradlew :catalogApp:assembleDebug                   → BUILD SUCCESSFUL
```

Last lines of the combined required run:
```
> Task :shared:testAndroidHostTest UP-TO-DATE
BUILD SUCCESSFUL in 1s
309 actionable tasks: 15 executed, 294 up-to-date
```

## Decisions

- **Entry point = Profile "Badges" row.** Spec §7 says "likely from Profile or Stats"; Profile is the
  settings surface and already has the row idiom (`FrSettingsRow`), so it was the lowest-friction,
  no-new-chrome choice. (Stats remains a candidate for a future quick-link.)
- **Celebration = `FrConfirmDialog`** (single Close action) per the spec's "simple FrConfirmDialog /
  dedicated celebration composable — no raw Material3 chrome". A richer confetti composable can replace
  it later without touching the ViewModel (it's purely effect-driven).
- **ISO date for "earned on".** Cross-platform localized month names aren't uniformly available in
  commonMain; ISO `yyyy-MM-dd` renders identically on both platforms inside the localized
  `EarnedOnFormat` wrapper.
- **Persist-in-ViewModel, not use case.** The use case stays a pure read pipeline; the single
  side-effecting write + celebrate lives in `AchievementsViewModel.persistAndCelebrate` (CHARTER §4/§9).

## Blockers

None. The feature is code-complete, wired, and links end-to-end.

## MANUAL step the USER must run (carried from `w2-badges-data`)

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```
The `accounts/{uid}/achievements/{id}` owner-only rule is in `firestore.rules` (emulator-tested).
**Until deployed, `observeUnlocks`/`recordUnlocks` get PERMISSION_DENIED** → the screen shows the read
error and persists nothing (maps to `AchievementError.Read.Unauthorized`).
