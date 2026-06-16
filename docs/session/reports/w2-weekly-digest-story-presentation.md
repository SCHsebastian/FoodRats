# Report — `w2-weekly-digest-story-presentation`

The LAST Wave 2 task. Adds a swipeable, Instagram-style weekly-recap STORY player that summarizes a
crew's week (top meal, best cook, most prolific, streak, badges unlocked this week, cuisines, "your
week"), reachable from the weekly-digest notification deep link AND an in-app Stats entry button.

## Status: DONE — all verify commands green.

## Layering decision (justified)

- **Player + scenes + ViewModel + recap model + Route + deep-link arm → `shared/` (app layer).**
  The recap is assembled from BOTH `ObserveStatsUseCase` (`:feature:stats`) AND
  `ObserveAchievementsUseCase` (`:feature:achievements`). Features may not depend on each other
  (CHARTER rule 2), so the player cannot live in `:feature:stats`. `shared` is the only place both
  use cases co-exist — it already aggregates every feature module and hosts cross-feature app UI
  (`ConsentScreen`/`ConsentViewModel`, `RootNavViewModel`). The recap player is presentation glue
  over two feature reads, not business logic.
- **`FrStoryProgressBar` + `FrStoryScaffold` → `:core:designsystem`** (primitives + lambdas only, no
  domain types). Both ship with catalog entries (`atom.storyprogress`, `atom.storyscaffold`) — the
  whole-project Konsist fitness function (`ArchitectureFitnessTest`) enforces a catalog entry for
  every public `Fr*`, so `FrStoryScaffold` needed one despite being full-screen chrome.
- **Recap read seam: `WeeklyRecapStream` (`fun interface`).** The ViewModel observes this, not the
  two concrete use cases — so it's unit-testable behind a trivial fake (the use cases pull a full
  Firebase graph). The production impl `statsAndAchievementsRecapStream` adapts the two observers
  through the pure `assembleWeeklyRecap`.

## What it does

- **Player** (`WeeklyStoryScreen` + `FrStoryScaffold`): full-screen scenes, segmented progress
  header (one pill per scene), 5s/scene auto-advance with an animated fill (clock lives in the
  Composable, dispatches `Advance`), tap-left=prev / tap-right=next, press-and-hold=pause, close X,
  Crossfade between scenes. Advancing past the last scene completes + dismisses.
- **Recap model** (`WeeklyRecap` / `RecapScene` / `RecapSceneKind`): an ordered list of render-ready
  scene VOs. Scene order (§2.4): cover → top meal → best cook → most prolific → streak → badges →
  cuisines → "your week". Cover + your-week are always present; award/streak/badge/cuisine scenes
  appear ONLY when their data exists — a quiet week degrades to cover + your-week (empty scenes
  skipped gracefully). Assembled PURELY by `assembleWeeklyRecap` — nothing recomputed; reuses the
  existing stats/achievements read paths. Badges scene filters `AchievementStatus.unlockedAtEpochMs`
  to the current ISO-week window. "Your week" uses ONLY personal facts (streak + cuisines + bingo
  counts, all derived over the member's own meals) — no other member's data leaks.
- **ViewModel** (`WeeklyStoryViewModel`): MVI single source of truth (scene index + paused live only
  in state); injects `WeeklyRecapStream` + `source` + `analytics = NoopAnalyticsTracker`. No
  `withContext`. Explicit `viewModel { ... analytics = get() }` (CHARTER §9).
- **Route + deep link** (`shared`): `Route.WeeklyStory(weekStart, fromNotification)` —
  `Route.Protected`, in `requiresSession()`; `parseDeepLink` arm `…/digest/{weekStart}` →
  `WeeklyStory(fromNotification=true)`. NavGraph `composable<Route.WeeklyStory>`. The weekly-digest
  push now carries `data.link = foodrats://app/digest/{weekStart}` (`functions/.../push.ts`
  `digestDeepLink` + `weeklyDigest.ts`); the existing Android/iOS tap handlers already forward
  `data.link` to `DeepLinkBus` generically (navigation-audit), so no platform code changed.
- **In-app entry**: a "See your week" `FrButton` at the top of `StatsScreen` → navigates to
  `WeeklyStory(weekStart="", fromNotification=false)` (the client derives the recap; `weekStart` is
  informational).
- **Analytics**: 3 new sealed leaves in `:core:domain` (`digest_story_opened` w/ `digest_source` +
  `scene_count`; `digest_story_scene_viewed` w/ `scene_kind` + `scene_index`;
  `digest_story_completed` w/ `scene_count`) + `DigestStorySource` dimension. snake_case, NO PII
  (scene-kind slugs + counts only). Added to `AnalyticsTaxonomyTest` + `TRACKING_PLAN.md`.
- **i18n**: all scene copy via `SharedStringKey` (24 keys) + `StatsStringKey.WeeklyRecapCta`, in
  BOTH `values/` and `values-es/` strings.xml. Screen-view auto-tracking picks up the new Route via
  the Route→snake_case mapping (`weekly_story`).

## Files changed

Design system (`:core:designsystem`):
- `src/commonMain/.../atoms/FrStoryProgressBar.kt` (new) — segmented Instagram progress bar atom.
- `src/commonMain/.../atoms/FrStoryScaffold.kt` (new) — full-screen story chrome (progress + close +
  tap/hold zones + scene slot).
- `src/androidHostTest/.../FrStoryProgressBarTest.kt` (new) — 3 Robolectric compose tests.

Catalog (`:catalogApp`):
- `stories/AtomStories.kt` — `atom.storyprogress` + `atom.storyscaffold` entries + stories.

Domain (`:core:domain`):
- `analytics/AnalyticsEvent.kt` — 3 new digest-story leaves.
- `analytics/AnalyticsDimensions.kt` — `DigestStorySource` enum.
- `src/commonTest/.../AnalyticsTaxonomyTest.kt` — 3 new leaves in `allEvents`.

Shared (`shared/`):
- `app/recap/WeeklyRecap.kt`, `WeeklyRecapAssembler.kt`, `WeeklyRecapStream.kt`,
  `WeeklyStoryContract.kt`, `WeeklyStoryViewModel.kt`, `WeeklyStoryScreen.kt`, `RecapScenes.kt`,
  `WeeklyStoryModule.kt` (all new).
- `app/navigation/Route.kt` — `Route.WeeklyStory` + `requiresSession` arm.
- `app/navigation/DeepLink.kt` — `SEGMENT_DIGEST` + parser arm.
- `app/navigation/NavGraph.kt` — `composable<Route.WeeklyStory>` + StatsScreen `onOpenRecap` wiring.
- `app/di/AppModule.kt` — registers `weeklyStoryModule`.
- `app/i18n/SharedStringKey.kt` + `composeResources/values{,-es}/strings.xml` — recap strings.
- `build.gradle.kts` — `coil.compose` (commonMain, top-meal photo) + `koin.test` (androidHostTest).
- `src/commonTest/.../recap/WeeklyStoryViewModelTest.kt` (7), `WeeklyRecapAssemblerTest.kt` (4).
- `src/commonTest/.../navigation/DeepLinkParserTest.kt` (+3), `RouteAccessTest.kt` (+WeeklyStory).
- `src/androidHostTest/.../recap/WeeklyStoryModuleVerifyTest.kt` (new, parameterized verify).

Stats (`:feature:stats`):
- `presentation/stats/StatsScreen.kt` — `onOpenRecap` param + "See your week" button.
- `i18n/StatsStringKey.kt` + `composeResources/values{,-es}/strings.xml` — `stats_weekly_recap_cta`.

Functions:
- `src/fcm/push.ts` — `digestDeepLink(weekStartIso)`.
- `src/triggers/weeklyDigest.ts` — `data.link = digestDeepLink(prevStartKey)`.
- `__tests__/weeklyDigest.test.ts` — 2 new digest-link tests.

Docs:
- `docs/analytics/TRACKING_PLAN.md` — 3 new live rows.

## Verification (all green)

- `./gradlew :feature:stats:testAndroidHostTest` → BUILD SUCCESSFUL.
- `./gradlew :shared:testAndroidHostTest` → BUILD SUCCESSFUL (new: WeeklyStoryViewModelTest 7/7,
  WeeklyRecapAssemblerTest 4/4, WeeklyStoryModuleVerifyTest 1/1, DeepLinkParserTest 12/12).
- `./gradlew :core:designsystem:testAndroidHostTest` → BUILD SUCCESSFUL (FrStoryProgressBarTest 3/3).
- `./gradlew :core:domain:testAndroidHostTest` → BUILD SUCCESSFUL (AnalyticsTaxonomyTest +
  ArchitectureFitnessTest catalog-coverage green).
- `pnpm --dir functions test` → 90 passed (weeklyDigest 9, incl. 2 new). `pnpm --dir functions build`
  → tsc clean.
- `./gradlew :androidApp:assembleDebug` + `:catalogApp:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :shared:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL (commonMain recap code
  compiles for iOS too).

## Decisions / notes

- Top-meal photo uses Coil 3 `AsyncImage` (added `coil.compose` to `shared` commonMain; the
  singleton `ImageLoader` is already installed app-wide by `installFeedImageLoader()`).
- `weekStart` in the Route/recap is informational only — the client derives the recap from its own
  read paths over the current ISO week (matches §2.4 "reuse client-side stats" option, avoiding a
  new `digests/{crewId}/{weekStart}` read path).
- The notification's title/body already exist server-side (`weeklyDigestTitle`/`Body`); only
  `data.link` was added, so no new server i18n key was needed. The client `PushPayloadMapper`
  already parses the digest; the deep link flows via the generic `data.link`→DeepLinkBus tap path.

## Blockers: none.

## MANUAL steps (carry to human.md)

- **Deploy the updated functions** so the weekly-digest push carries the recap deep link:
  `pnpm --dir functions build && pnpm dlx firebase-tools deploy --only functions:weeklyDigest --project foodrats-de4ec`
  (until deployed, the digest push has no `link` and a tap just opens the app). No new Firestore
  rules/indexes/seed needed — the story reads the same data Stats/Achievements already read.
- (Pre-existing, unchanged) achievements unlock rule + cuisine/ingredient seed deploys from earlier
  Wave 2 tasks still gate the badges/cuisines scenes showing real data.
