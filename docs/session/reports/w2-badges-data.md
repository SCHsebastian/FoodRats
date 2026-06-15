# Report — `w2-badges-data`

DATA/INFRA layer for the achievements engine. Builds on the already-complete `w2-badges-domain`
output (the `feature/achievements/` module was scaffolded + domain types/evaluator/catalog/i18n were
green on disk). Spec: `docs/specs/2026-06-14-badges-achievements-design.md` §6 (persistence), §16
(rules). Handoff read: `docs/session/handoffs/w2-badges-domain.md`.

## Status: DONE, verified green.

## What I did

### `:core:domain` — the port (spec §6.1)
- `core/domain/.../achievement/AchievementProgressPort.kt` — the cross-context contract the
  presentation layer observes. Vendor-free (only `AccountId`, `Result`, `Flow`):
  - `fun observeUnlocks(accountId): Flow<Result<Map<String, Long>, AchievementProgressError>>`
    — persisted unlocks keyed by **raw String id** (the Firestore doc id) → unlock epoch-ms.
    Keyed by `String`, not `AchievementId`, so the port holds no feature type; the feature maps
    `String → AchievementId` against its catalog on read (drop-on-read for unknown ids).
  - `suspend fun recordUnlocks(accountId, newlyUnlocked: Map<String, Long>): Result<Unit, ...>`
    — idempotent batch write; empty map = no-op returning `Ok`.
- `core/domain/.../achievement/AchievementProgressError.kt` — sealed interface (never enum):
  `Unauthorized`, `Unavailable`.
- Konsist passes (the new files import nothing forbidden).

### `:feature:achievements` build (spec §4, domain-task TODO)
- `build.gradle.kts`: bumped `jvmTarget` JVM_11 → **JVM_17** (Firebase BOM inline funcs);
  added `implementation(libs.firebase.firestore)` to `commonMain` and the **Firebase BOM**
  (`platform(libs.firebase.bom)`) to a new `androidMain.dependencies` block.

### `:feature:achievements` data layer (spec §6.2)
- `data/firebase/AchievementUnlockDto.kt` — `@Serializable data class AchievementUnlockDto(val unlockedAtEpochMs: Long = 0L)`.
  Doc id IS the raw achievement id, so it is not a body field.
- `data/firebase/AchievementFirestoreDataSource.kt` — the only GitLive-touching adapter. Targets
  `accounts/{uid}/achievements/{id}`. Streams via `.snapshots` (cold, no `withContext`), writes via
  `firestore.batch().apply { set(...) }.commit()`. Exposes an internal `AchievementUnlockStore`
  interface (the vendor-free seam) so the repository is host-testable.
- `data/firebase/AchievementErrorMapper.kt` — message-substring bucketing (matching `CrewErrorMapper`):
  permission/unauthenticated → `Unauthorized`, else → `Unavailable`.
- `data/repository/FirebaseAchievementRepository.kt` — implements `AchievementProgressPort`.
  `observeUnlocks` = `map → Ok` + `.catch { Err(mapper.map(it)) }` (NO `withContext`).
  `recordUnlocks` = one `withContext(dispatchers.io)` + `runCatching{}.fold`; empty short-circuits.

### Reconcile placement (DECISION — see below)
- `domain/AchievementReconciler.kt` — pure overlay (spec §6.3): takes evaluator output + persisted
  map + `now`, stamps persisted dates as earned, and collects met-but-unpersisted ids → `now`.
  Returns `Reconciled(statuses, newlyUnlocked)`. The ViewModel (presentation task) wires
  `evaluate → reconcile → recordUnlocks`.

### Koin + registration (spec §14)
- `di/AchievementsModule.kt` — binds `AchievementEvaluator`, `AchievementReconciler`,
  `AchievementErrorMapper`, `AchievementUnlockStore` (→ `AchievementFirestoreDataSource`), and
  `AchievementProgressPort` (→ `FirebaseAchievementRepository`). The use case + `viewModel{}` are
  left for the presentation task (noted in a comment).
- `shared/.../app/di/AppModule.kt` — imported + added `achievementsModule` to `appModules`.
- `shared/build.gradle.kts` — added `implementation(projects.feature.achievements)` (was in
  `settings.gradle.kts` but NOT a shared dep — the domain task left it out since `shared` didn't
  yet reference it).

### Firestore rules (spec §16)
- `firestore.rules` — new `accounts/{uid}/achievements/{achievementId}` block, **owner-only on read
  AND write** (`request.auth.uid == uid`). Stricter than `accounts/{uid}` itself (achievements are
  private to the owner, like `/private` and `/devices`); earning is client-derived so the owner
  legitimately writes.
- `firestore-tests/tests/achievements.test.ts` — 5 cases (owner read/write succeed; cross-user
  read/write fail; unauth read fails).

### Tests added
- `commonTest` `AchievementUnlockDtoTest` (4) — serialization round-trip, single-field shape,
  default-on-missing, unknown-field tolerance.
- `commonTest` `FirebaseAchievementRepositoryTest` (7) — observe Ok-map + permission→Unauthorized +
  other→Unavailable; record writes + Ok; empty no-op; failure→typed; write surfaces on the read
  stream (the §6.3 earned-flip shape). Uses a `FakeUnlockStore` + test `DispatcherProvider`.
- `commonTest` `AchievementReconcilerTest` (4) — persisted→earned (no re-fire); newly-met collected
  with `now` (unstamped this frame); locked stays uncollected; mixed-set partition.
- `androidHostTest` `AchievementsModuleVerifyTest` (1) — `verify(extraTypes = [FirebaseFirestore,
  DispatcherProvider])`.

## Decisions

1. **Repository is THIN; reconcile is a pure helper, not in the repo (SPEC WINS over the task brief).**
   The task brief said "the repository should combine: read meal signals → run evaluator →
   reconcile". The spec (§6.2/§6.3/§7) is explicit and authoritative: the repository is a thin
   `AchievementProgressPort` impl (one `withContext` per method), the **ViewModel** runs the reactive
   `combine → flatMapLatest → evaluate → overlay → recordUnlocks` pipeline, and the evaluator stays
   pure. Per CLAUDE.md "spec wins", I followed §6. To still satisfy the brief's intent (a testable
   reconcile unit owned by the data/engine side, not buried in the ViewModel), I extracted the pure
   `AchievementReconciler` as the documented overlay seam. This keeps: evaluator pure, repository
   one-I/O-per-method, reconcile unit-tested here, and the ViewModel a thin wirer.
2. **`AchievementUnlockStore` interface** introduced as the vendor seam so the repository is
   host-testable (GitLive Firestore can't run in a JVM host test). Mirrors the datasource/repository
   split in `:feature:crew`/`:feature:meal`.
3. **`AchievementErrorMapper`** is a feature-local class (not `:core:domain`) — it maps a vendor
   `Throwable` to the `AchievementProgressError` declared in `:core:domain`, same as `CrewErrorMapper`.

## Verify (all green)

```
./gradlew :core:domain:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 17s
20 actionable tasks: 15 executed, 1 from cache, 4 up-to-date
```

```
./gradlew :feature:achievements:testAndroidHostTest
> Task :feature:achievements:testAndroidHostTest
BUILD SUCCESSFUL in 1s
90 actionable tasks: 8 executed, 82 up-to-date
```
New-file counts from the JUnit XML: `AchievementsModuleVerifyTest` 1/0/0, `FirebaseAchievementRepositoryTest`
7/0/0, `AchievementUnlockDtoTest` 4/0/0, `AchievementReconcilerTest` 4/0/0 (tests/failures/errors).

```
cd firestore-tests && pnpm test
 Test Files  5 passed (5)
      Tests  38 passed (38)
✔  Script exited successfully (code 0)
```
(`achievements.test.ts` 5/5; PERMISSION_DENIED log lines are the expected `assertFails` cases.)

## Blockers
None.

## Manual step the user must run (not codeable here)
- Deploy the rules: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`.
  **Until deployed, `observeUnlocks`/`recordUnlocks` are PERMISSION_DENIED** — the feature shows the
  read error and persists nothing.

## Left for `w2-badges-presentation`
- `ObserveAchievementsUseCase` (wires `MealReadPort.observeRange` + `ActiveCrewProvider` +
  `SessionProvider` + a feature-local `AchievementSignalsBuilder` for streaks/best-cook), the
  reactive ViewModel (`combine → flatMapLatest → evaluate → AchievementReconciler.reconcile →
  recordUnlocks → emit Unlocked`), `AchievementsContract/Screen`, `FrBadge` + catalog story,
  `AnalyticsEvent.AchievementUnlocked` leaf, and ADD into `achievementsModule`:
  `factoryOf(::ObserveAchievementsUseCase)` + the explicit `viewModel { ... analytics = get() }`.
  When it does, extend `AchievementsModuleVerifyTest.extraTypes` with `MealReadPort`,
  `ActiveCrewProvider`, `SessionProvider`, `Clock`, `AnalyticsPort`.
- `:androidApp` may need `implementation(projects.feature.achievements)` if the NavGraph references
  the screen directly (mirrors `:feature:mealAi`).
