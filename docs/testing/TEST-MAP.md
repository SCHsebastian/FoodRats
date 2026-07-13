# FoodRats Test Map

Authoritative feature × layer map of the test suite. Agents: the `[test-map]` PostToolUse
hook (`.claude/hooks/test-map.sh` → `scripts/test/affected-tests.sh`) tells you which task
covers a file you edit; this document tells you where the tests for each layer live and
what pattern to follow when adding or updating them. Inventory snapshot: 2026-07-13
(~240 Kotlin test files + 14 vitest specs).

## Commands

| Scope | Command |
|---|---|
| One module | `./gradlew :feature:meal:testAndroidHostTest` |
| One test class | `./gradlew :feature:meal:testAndroidHostTest --tests "*PublishMealUseCaseTest*"` |
| Full host suite (every module with a host-test task; mirrors ci.yml host-tests) | `./gradlew :core:domain:testAndroidHostTest :core:data:testAndroidHostTest :core:database:testAndroidHostTest :core:designsystem:testAndroidHostTest :core:presentation:testAndroidHostTest :core:i18n:testAndroidHostTest :feature:auth:testAndroidHostTest :feature:crew:testAndroidHostTest :feature:meal:testAndroidHostTest :feature:feed:testAndroidHostTest :feature:stats:testAndroidHostTest :feature:notifications:testAndroidHostTest :feature:ingredient:testAndroidHostTest :feature:achievements:testAndroidHostTest :feature:moderation:testAndroidHostTest :feature:meal-ai:testAndroidHostTest :shared:testAndroidHostTest` |
| iOS (Firebase-free modules only) | `./gradlew :core:domain:iosSimulatorArm64Test :core:presentation:iosSimulatorArm64Test` |
| Cloud Functions | `pnpm --dir functions test` (vitest) + `pnpm --dir functions build` (tsc) |
| Android build gate | `./gradlew :androidApp:assembleDebug` |
| iOS link gate | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |

Note: `commonTest` sources run through `testAndroidHostTest` (there is no `:module:test`
task). Modules that transitively touch Firebase cannot run `iosSimulatorArm64Test`
(no `FirebaseCore.framework` at the Gradle link boundary).

## Layer conventions (what to add where)

| Layer | Source set | Pattern / reference |
|---|---|---|
| Domain: value objects, policies | `src/commonTest/.../domain/` | `core/domain/.../meal/ScoreTest.kt`, `crew/CrewNameTest.kt` |
| Domain: use cases | `src/commonTest/.../domain/usecase/` | `feature/meal/.../PublishMealUseCaseTest.kt` — fakes over ports, `FixedClock`, assert on `Result<T,E>` leaves |
| Data: repositories, mappers, DTOs | `src/commonTest/.../data/` | `feature/crew/.../data/firebase/CrewMapperTest.kt`, `FirebaseCrewRepositoryTest.kt` over a `Fake*DataSource` |
| Data: outbox / sync / offline | `src/commonTest` or `androidHostTest` (`.../data/sync|queue|outbox/`) | `core/data/.../outbox/OutboxRunnerTest.kt`, `feature/meal/.../data/sync/MealSyncEngineTest.kt` |
| Presentation: ViewModels (MVI) | `src/commonTest/.../presentation/` | Turbine + `UnconfinedTestDispatcher`; assert with `expectMostRecentItem()` (never a transient `awaitItem()`). Reference: `FeedViewModelTest.kt` |
| Presentation: error → string exhaustiveness | `src/commonTest/.../presentation/*ErrorToStringKeyTest.kt` | one per `<Feature>Error` tree; MUST be updated with every new error leaf |
| UI: Compose behavior | `src/androidHostTest/` + `robolectric.properties` (sdk=33 + display qualifiers) | `createComposeRule` from the **v2** package; `core/designsystem/.../atoms/FrButtonTest.kt`, `feature/ingredient/.../SelectIngredientsScreenTest.kt` |
| DI: Koin graph | `src/androidHostTest/.../di/*ModuleVerifyTest.kt` | every feature module; add `extraTypes` when a default-arg port is injected explicitly |
| Architecture (Konsist) | `core/domain/src/androidHostTest/` only | `KonsistRulesTest.kt` + `ArchitectureFitnessTest.kt` (7 rules: no cross-feature imports, dispatcher boundary, no hardcoded UI text, catalog entries, …) |
| Server | `functions/__tests__/*.test.ts` | vitest; one spec per trigger/callable |

## Feature × layer coverage matrix

Legend: ✅ present · ➖ thin (≤1 file) · ❌ missing. "CI" = runs in `.github/workflows/ci.yml` today.

| Module | Domain | Data | ViewModel | Error→Key | UI (Robolectric) | DI verify | CI |
|---|---|---|---|---|---|---|---|
| core/domain | ✅ (VOs, policies, Result) | — | — | — | — | — | ✅ |
| core/data | — | ✅ (outbox, prefs, analytics) | — | — | — | — | ✅ (added 2026-07-13) |
| core/database | — | ✅ (schema, migration) | — | — | — | — | ✅ (added 2026-07-13) |
| core/designsystem | — | — | — | — | ✅ (~26 `Fr*Test`) | — | ✅ |
| core/presentation | — | — | ✅ MVI base | — | — | — | ✅ (host task enabled 2026-07-13) |
| core/i18n | — | — | — | ✅ formatting | — | — | ✅ (added 2026-07-13) |
| feature/auth | ✅ | ✅ (self-heal, outbox) | ✅ (3 VMs) | ✅ (2) | ❌ | ✅ | ✅ |
| feature/crew | ✅ (~18 UC + VOs) | ✅ (+sync) | ✅ (3 VMs) | ✅ | ❌ | ✅ | ✅ |
| feature/meal | ✅ | ✅ (queue, upload, sync) | ✅ (3 VMs) | ✅ (2) | ❌ | ✅ | ✅ |
| feature/feed | ✅ | ➖ | ✅ (2 VMs) | ✅ (6) | ➖ (`FrUploadQueueBarTest`) | ✅ | ✅ |
| feature/stats | ✅ (streak compute) | ❌ | ✅ | ✅ | ❌ | ✅ | ✅ |
| feature/notifications | ✅ | ✅ (adapter, payload) | ✅ | ✅ | ❌ | ✅ | ✅ |
| feature/ingredient | ✅ | ✅ | ✅ | ✅ (2) | ✅ (2) | ✅ (2) | ✅ |
| feature/meal-ai | ❌ | ➖ (bitmap lifecycle) | — | — | — | ❌ | ✅ (added 2026-07-13) |
| feature/achievements | ✅ (evaluator, reconciler) | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ (added 2026-07-13) |
| feature/moderation | ❌ | ✅ (block, report) | ❌ | ✅ | ❌ | ✅ | ✅ (added 2026-07-13) |
| shared (nav/root) | ✅ (deep links, route access) | — | ✅ (3 VMs) | — | ➖ (back stack, banner) | ✅ (2) | ✅ |
| androidApp | — | — | — | — | ❌ (no test source set) | — | build only |
| functions/ | ✅ vitest specs (exhaustive edge-case pass 2026-07-13) | | | | | | ✅ `functions-tests` job (added 2026-07-13) |

## Test doubles

Reusable doubles live in `commonMain` of core modules: `FixedClock` (`core/domain/.../time/Clock.kt`),
`RecordingAnalyticsTracker`, `NoopCrashReporter`, `RecordingStoryShareController`. Each feature
carries its own fakes (`.../domain/test/`, `.../testdoubles/`): `FakeCrewRepository`,
`FakeMealRepository`, `FakeConnectivityPort`, `RecordingOutboxPort`, etc. — follow the local
feature's naming when adding one. DB harnesses: `core/data/.../outbox/OutboxTestDb.kt`,
`core/database/.../TestDriverFactory.*`.

## Emulator / on-device lanes (current state)

- **No instrumented test source sets exist.** The only managed device is
  `pixel6Api34` (Pixel 6, API 34, aosp-atd) in `baselineprofile/build.gradle.kts`,
  used for `:androidApp:generateBaselineProfile`.
- Ad-hoc adb UI driving: `docs/session/2026-06-19-functional-review/ui.py`
  (uiautomator-dump parser; copies in 2026-06-24/2026-06-25 sessions), plus the two-user
  runbook in `docs/session/2026-06-19-functional-review/`. Emulator test accounts:
  owner@a.com / user@a.com / a@a.com, password 123456 (crew "walk crew").
- The release smoke walk (sign-in → crew → publish meal → feed → stats → notification)
  is manual and mandatory after touching R8/minify config.
- Building an automated emulator smoke lane is Phase 3 of
  `docs/session/2026-07-13-test-orchestration/ORCHESTRATOR-PLAN.md`.

## Known gaps (feed the orchestrator plan; fix opportunistically when touching the area)

1. ~~CI host-tests missing modules~~ — FIXED 2026-07-13: all 17 host-test tasks run in CI;
   `:core:presentation` got `withHostTest` enabled; `:feature:meal-ai` runs (the old
   NO-SOURCE comment was stale — `ClassifierBitmapLifecycleTest` executes, 3 tests).
2. ~~functions/ not in CI~~ — FIXED 2026-07-13: `functions-tests` job (pnpm install →
   tsc → vitest), fork-safe.
3. `:feature:meal-ai` domain coverage is still thin (bitmap lifecycle only); the
   classifier→slug mapping and `ClassifyDraftPlateUseCase` advisory behavior live in
   `:feature:meal` tests — extend under Phase 1 of the orchestrator plan.
4. Zero screen-level UI tests for auth, crew, meal, stats, notifications, moderation
   (only ingredient + designsystem + slivers of feed/shared have Robolectric UI tests).
5. No instrumented/emulator lane; the publish-meal → feed → stats end-to-end path is
   verified only by hand.
