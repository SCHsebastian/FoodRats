# Report — w5-offline-compose-data

DATA/INFRA layer for offline-first compose (roadmap §5.2). Durable local persistence of the
draft queue, a connectivity-gated background retry runner, and a queued/failed count surfaced
through the existing cross-feature upload-progress port.

## What was built

### `:core:domain` (additive, default-safe)
- **`meal/MealUploadQueueSnapshot.kt`** (new) — small cross-feature aggregate `(pending, terminalFailed)`
  with `hasWork` + `EMPTY`. `pending` = `Pending` + `Uploading` + retryable `Failed`; `terminalFailed`
  = `Failed(retryable = false)`. This is the *extension* the roadmap asked for (it says "extend
  `MealUploadProgressPort` states"); I added a sibling snapshot type rather than mutating the sealed
  `MealUploadStatus` so no consumer's `when` can break.
- **`meal/MealUploadProgressPort.kt`** — added `val queue: StateFlow<MealUploadQueueSnapshot>` **with a
  default** (`MutableStateFlow(EMPTY)`). The default keeps every existing implementer/test-fake
  (FeedViewModelTest, StatsViewModelTest) compiling untouched; the durable-queue coordinator overrides it.

### `:core:data`
- **`datastore/Keys.kt`** — added `DraftQueueJson` string key (the durable queue blob), distinct from
  the single-draft `MealDraftJson` and the legacy single-flag `MealUploadPending`.

### `:feature:meal` (the task body)
- **`data/queue/DraftQueueLocalStore.kt`** (new) — DataStore-backed JSON (de)serialization of the full
  `List<QueuedDraft>` incl. base64 plate bytes, status, attempt count, timestamps. Pure read/modify/write
  helper (no `withContext`); mirrors the proven `MealDraftLocalStore` shape. **No SQLDelight in the
  repo** (checked `libs.versions.toml` + build files) — DataStore JSON is the available, process-death-
  surviving mechanism, so I used it rather than adding a DB dependency.
- **`data/queue/DraftQueueRepository.kt`** (new) — implements the domain `DraftQueuePort`. The single IO
  boundary: **exactly one `withContext(dispatchers.io)` per public method** (CLAUDE.md rule). Generates
  `QueueEntryId` via `kotlin.uuid.Uuid.random()`, stamps `createdAt`/`lastAttemptAt` from the injected
  `Clock`, owns the `attemptCount` bookkeeping (`markFailed` increments; 1-based = "attempts already
  failed", matching `DraftRetryPolicy`). DataStore IO failures map to `MealError.Publish.PublishUnavailable`
  (no new error leaf — the domain task confirmed none is in scope).
- **`data/queue/DraftRetryRunner.kt`** (new) — pure-Kotlin orchestration shared by both platforms.
  `runOnce(scope)` drains every `Pending` entry: `markUploading` → `MealRepository.publish` (idempotent
  via deterministic `MealId.forDaySlot`) → `Ok` removes (reconcile-on-success); `Err` increments via
  `DraftQueueTransitions.onFailure(newAttemptCount, errorKey, policy)` and `markFailed`, then if still
  retryable schedules a backed-off (`DraftRetryPolicy.nextDelay`) flip back to `Pending`, else leaves the
  terminal `Failed(retryable = false)`. Returns `true` iff nothing drainable remains (Pending/Uploading/
  retryable-Failed) — the Android worker maps `true`→success, `false`→retry. `start(scope)` wires two
  triggers onto the app scope: connectivity-online edge + a new-Pending-entry edge. Also exposes
  `snapshotOf(list)` (the `MealUploadQueueSnapshot` derivation) and the shared `MealError.uploadErrorKey()`
  token mapper (identical tokens to `BackgroundMealUploadCoordinator`).
- **`data/queue/ConnectivityMonitor.kt`** (new, common interface) + **`AndroidConnectivityMonitor`**
  (`ConnectivityManager.NetworkCallback`, VALIDATED+INTERNET caps) + **`IosConnectivityMonitor`**
  (`NWPathMonitor`, satisfied status). Both emit the current value on subscribe, conflated +
  distinctUntilChanged. This is the in-process counterpart to WorkManager's `NetworkType.CONNECTED`.
- **`data/upload/BackgroundMealUploadCoordinator.kt`** — converged with the queue rather than duplicating:
  new **nullable** `draftQueue`/`retryRunner` ctor params (nullable so existing direct construction stays
  green). It now (a) overrides `queue` with a live flow `draftQueue.observe() → snapshotOf`, (b) starts the
  retry runner's triggers in `init`, and (c) on `enqueueDraftUpload()` durably enqueues the current draft
  **in addition to** the existing immediate single-flag fast path. The two paths can't duplicate: both
  target the same deterministic `MealId`, so a race is a harmless overwrite; the runner removes the entry
  on its own success.
- **`data/upload/MealUploadWorker.kt`** (Android) — now also drains the durable queue
  (`retryRunner.runOnce(scope = null)` so WorkManager backoff, not an in-process delay, drives retries
  across process death); `Result.retry` iff either path is undrained.
- **Koin** — `mealModule` binds `DraftQueueLocalStore`, `DraftQueuePort` → `DraftQueueRepository`,
  `DraftRetryPolicy()`, `DraftRetryRunner`, and threads `draftQueue`/`retryRunner` into the coordinator.
  `mealAndroidModule`/`mealIosModule` bind `ConnectivityMonitor` per platform. `MealModuleVerifyTest`
  `extraTypes` gained `ConnectivityMonitor::class` (the platform-provided dependency).

### Tests (all in `:feature:meal` commonTest)
- **`DraftQueueRepositoryTest`** — enqueue → persisted → observed; **survives a fresh store instance over
  the same backing data (process-death proxy)** incl. the durable plate bytes; markUploading→markFailed
  increments attempt + sets Failed; remove dequeues (no-op-safe); observe orders by `createdAt`.
- **`DraftRetryRunnerTest`** — pending→publish-success removes; failure→attempt=1 + retryable; max-attempts
  (2) → terminal `Failed(retryable=false)` after 2 publishes; idempotent fail-then-success removes only on
  Ok (deterministic `MealId` stable across retries); `snapshotOf` counts pending(2)/terminalFailed(1).

## Decisions
- **DataStore JSON, not SQLDelight** — SQLDelight is not in the repo; §5.2 says "DataStore or SQLDelight";
  DataStore Preferences survives process death and matches existing infra (`MealDraftLocalStore`). No new dep.
- **Durable image = base64 in the JSON blob**, mirroring the single-draft store. The handoff allowed
  "the `Plate` bytes (or a file ref)". A file-ref over `okio` + a persistent files-dir (the repo has an
  `internal` cache-dir `expect/actual` in `:core:data`) is the future optimization if plate payloads
  bloat the DataStore blob; called out as tech-debt, not needed for correctness now.
- **Extended via a sibling `MealUploadQueueSnapshot` + a defaulted port member**, not by mutating the sealed
  `MealUploadStatus`. Zero blast radius on existing consumers/fakes (verified: feed/core:domain green).
- **Convergence with the existing coordinator** (not a parallel path): the deterministic-`MealId`
  idempotency makes the two upload paths safe to co-exist; the queue is the offline/failure safety net.
- **Connectivity: a small per-platform `ConnectivityMonitor`** (Android `ConnectivityManager` / iOS
  `NWPathMonitor`) to trigger in-process drain on reconnect, complementing WorkManager's
  `NetworkType.CONNECTED` constraint (which already covers the after-process-death wakeup on Android).

## Verify (all run; last lines quoted)
- `./gradlew :feature:meal:testAndroidHostTest` → `BUILD SUCCESSFUL in 4s` · `150 tests completed` (was a
  1-test fail on the runner's "drained?" calc treating retryable-Failed as done — fixed to count it as
  undrained; re-ran green).
- `./gradlew :core:domain:testAndroidHostTest :feature:feed:testAndroidHostTest` →
  `:feature:feed:testAndroidHostTest` · `BUILD SUCCESSFUL in 5s`.
- `./gradlew :androidApp:assembleDebug` → `BUILD SUCCESSFUL in 3s`.
- `./gradlew :shared:compileKotlinIosSimulatorArm64` → `BUILD SUCCESSFUL in 8s` (incl.
  `:feature:meal:compileKotlinIosSimulatorArm64` — the `IosConnectivityMonitor` NWPathMonitor cinterop links).

## Blockers / notes
- **`:feature:stats:compileAndroidHostTest` is RED — but PRE-EXISTING and INDEPENDENT of this task.**
  15 compile errors, all `Meal(...)` constructor arg mismatches (`DishName`/`Description`/`Instant`/
  `publishedAt`) in `PersonalStreakTest.kt` + `StatsViewModelTest.kt`, from a prior incomplete task's
  `Meal` change. **Proven independent:** reverting `MealUploadProgressPort.kt` to HEAD and recompiling
  stats still yields the same 15 errors. I touched no stats file and no `Meal.kt`. Not fixed here (out of
  scope); flag for the orchestrator — stats host tests will stay red until those fixtures are updated.
- **iOS background is best-effort.** `NWPathMonitor` (and the in-process runner) only fire while the app
  is alive/foreground; iOS grants no general background execution without `BGTaskScheduler` + Info.plist/
  entitlements (consistent with the existing `InProcessMealUploadScheduler` no-op). An offline-composed
  plate publishes on the next foreground reconnect. Documented in `IosConnectivityMonitor` KDoc.
- **Real WorkManager + real connectivity is a MANUAL on-device check** (host tests use fakes). Appended to
  `docs/session/human.md`.
