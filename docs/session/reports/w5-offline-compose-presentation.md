# Report — w5-offline-compose-presentation (roadmap §5.2, terminal offline-compose task)

## Status: DONE — verified green

Surfaced the offline-first publish queue in the feed top bar: a pending/failed
indicator with retry + dismiss affordances, plus an idempotency de-dup so a queued
draft that actually published renders once, not twice. This is the last
offline-compose task; the data + domain layers were already in place (see the two
handoffs).

## What was built

### 1. Cross-feature write port for retry/dismiss (`:core:domain`)
- **NEW** `core/domain/.../meal/QueuedUploadActionsPort.kt` — a tiny port with
  `suspend fun retryFailed()` and `suspend fun dismissFailed()`. Both target the
  **terminal** `Failed(retryable = false)` entries (the runner gave up; only those
  need a user decision). The data handoff explicitly said "declare a tiny
  `:core:domain` write port and implement it on the coordinator" if the bar needs
  buttons — §5.2 calls for retry/cancel, so it does.
- Konsist (`:core:domain:testAndroidHostTest`) confirms no forbidden imports.

### 2. Port implementation on the data-layer choke point (`:feature:meal`)
- `BackgroundMealUploadCoordinator` now also implements `QueuedUploadActionsPort`
  (it already held `draftQueue: DraftQueuePort?`). `retryFailed()` flips every
  terminal entry back to `Pending` via `updateStatus(id, Pending)` — that re-arms
  the existing `DraftRetryRunner`'s queue-observer, which drains idempotently
  (deterministic `MealId.forDaySlot` → overwrite, never duplicate). `dismissFailed()`
  calls `remove(id)`. Choke point stays in the data layer; the UI only calls the port.
- `MealModule.kt`: bound `single<QueuedUploadActionsPort> { get<BackgroundMealUploadCoordinator>() }`
  (same singleton already bound for `MealUploadCoordinator` + `MealUploadProgressPort`).
  Not added to `MealModuleVerifyTest.extraTypes` — it's a *bound* type, not an
  external dependency.

### 3. FeedViewModel — observe the snapshot + de-dup (MVI single source of truth)
- Injected `queuedUploadActions: QueuedUploadActionsPort` (positional, via the
  existing explicit `viewModel { }`).
- Observe `uploadProgress.queue` (the `MealUploadQueueSnapshot` the data task
  publishes through the existing `MealUploadProgressPort.queue`) → folds
  `pending`/`terminalFailed` into new `FeedState.queuedPending` / `queuedFailed`.
  No parallel `MutableStateFlow`; reducer-only updates. (No `distinctUntilChanged()`
  on it — `queue` is a `StateFlow`, which already conflates; applying it is a
  deprecation-warning no-op that fails the warning-as-error build.)
- **Idempotency reconcile:** when building the feed UI list, added
  `.distinctBy { it.mealId }`. The deterministic `MealId` means a published queued
  draft is the *same* meal as its eventual feed row, so a transient double-emit
  would otherwise (a) double-render and (b) crash the LazyColumn's `key = { it.mealId }`.
  The indicator itself clears automatically: the runner removes an entry on publish
  success, so its queue count drops to 0 — it is never double-counted against the
  now-authoritative meal.
- Two new intents: `FeedIntent.RetryQueuedDrafts` → `queuedUploadActions.retryFailed()`,
  `FeedIntent.DismissQueuedDrafts` → `dismissFailed()`.

### 4. Top-bar indicator UI (feature-owned, design-system atoms only)
- **NEW** `feature/feed/.../presentation/components/FrUploadQueueBar.kt` — a
  domain-aware feed component (resolves `FeedStringKey`, like `FrFeedMealRow`; it
  lives in the feature, not `:core:designsystem`, because it speaks the feature's
  i18n). Built from `:core:designsystem` atoms only (`FrText`, `FrIcon`, `FrButton`,
  `FrProgressIndicator`, `FrIcons.Warning`) + `LocalFrSemanticColors` (`danger`/`info`
  meaning roles) — **no raw Material3 chrome**.
  - Empty (`pending<=0 && failed<=0`) → renders nothing.
  - `failed > 0` → danger banner "N failed to post" + Retry + Dismiss (Ghost buttons).
  - `pending > 0` → info row "N waiting to publish" + a small spinner.
  - `liveRegion = Polite` for TalkBack.
- `FeedScreen` renders it in the day-header column, just below the existing
  `FrUploadProgressBar` (consistent with the in-flight single-upload UX, not
  duplicative — that bar reflects `status is Uploading`, this reflects the durable
  queue counts).

### 5. i18n (both locales)
- `FeedStringKey`: `QueuePending`, `QueueFailed`, `QueueRetryCta`, `QueueDismissCta`.
- `values/strings.xml` + `values-es/strings.xml`: `feed_queue_pending`
  (`"%1$d waiting to publish"` / `"%1$d esperando para publicar"`),
  `feed_queue_failed`, `feed_queue_retry_cta`, `feed_queue_dismiss_cta`. Counts are
  templated `%1$d`, not concatenated.

### 6. Koin wiring + verify test
- `FeedModule.kt`: passes `queuedUploadActions = get()`.
- `FeedModuleVerifyTest.extraTypes`: added `QueuedUploadActionsPort::class`.

## Decisions

- **Retry/dismiss target only terminal `Failed(retryable = false)` entries.**
  Pending / retryable-failed entries resolve on their own once connectivity returns;
  exposing buttons for them would be misleading. The data handoff's recommended
  port semantics (`retry = updateStatus(id, Pending)`, `dismiss = remove(id)`)
  applied to the terminal subset.
- **No analytics event added.** §5.2 does not call for a `draft_retry`/`draft_canceled`
  leaf (CHARTER rule 9 + the task brief: "only if §5.2 calls for it"). Not added.
- **Aggregate count only, no per-entry sheet.** The handoff recommended the aggregate
  for a count badge; a per-entry surface would need either a `:feature:meal` dep
  (banned) or a richer new port. The aggregate snapshot is sufficient for the bar.
- **De-dup at the ViewModel UI-mapping step**, not in the read port — the feature
  owns its render contract, and `distinctBy { it.mealId }` is the cheapest correct
  fix that also guards the LazyColumn key invariant.

## Tests added (`FeedViewModelTest`, all green)
- `queue_snapshot_pending_count_is_surfaced_in_state` — pending=2 shows in state.
- `queue_empty_keeps_both_counts_zero` — empty → hidden (both counts 0).
- `terminal_failed_count_is_surfaced_and_retry_invokes_port` — failed=1 surfaced;
  `RetryQueuedDrafts` calls `retryFailed()` once.
- `dismiss_queued_drafts_invokes_port` — `DismissQueuedDrafts` calls `dismissFailed()`.
- `published_queued_draft_is_not_double_rendered` — read port emits the same MealId
  twice → exactly 1 row (idempotency/de-dup).
- New test fakes: `FakeUploadProgressPort` (drivable `queue` snapshot),
  `FakeQueuedUploadActionsPort` (records retry/dismiss). `buildVm` extended with
  `uploadProgress` + `queuedActions` params (defaults keep all existing tests green).

## Verify (commands + last lines quoted)

`./gradlew :feature:feed:testAndroidHostTest`
```
> Task :feature:feed:testAndroidHostTest
BUILD SUCCESSFUL in 7s
91 actionable tasks: 12 executed, 79 up-to-date
```

`./gradlew :feature:meal:testAndroidHostTest :core:domain:testAndroidHostTest`
(meal port impl + binding; core:domain new port + Konsist)
```
> Task :feature:meal:testAndroidHostTest
> Task :core:domain:testAndroidHostTest
BUILD SUCCESSFUL in 17s
102 actionable tasks: 19 executed, 83 up-to-date
```

`./gradlew :androidApp:assembleDebug` (full Koin graph wires up end-to-end)
```
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 3s
329 actionable tasks: 81 executed, 248 up-to-date
```

`:core:designsystem:testAndroidHostTest` was **not** run — no designsystem chrome
was touched (`FrUploadQueueBar` lives in the feature module, per the catalog
convention that feature/Firebase-coupled components stay out of the catalog APK).

## Files changed
- `core/domain/.../meal/QueuedUploadActionsPort.kt` (new)
- `feature/meal/.../data/upload/BackgroundMealUploadCoordinator.kt`
- `feature/meal/.../di/MealModule.kt`
- `feature/feed/.../presentation/feed/FeedContract.kt`
- `feature/feed/.../presentation/feed/FeedViewModel.kt`
- `feature/feed/.../presentation/feed/FeedScreen.kt`
- `feature/feed/.../presentation/components/FrUploadQueueBar.kt` (new)
- `feature/feed/.../i18n/FeedStringKey.kt`
- `feature/feed/.../composeResources/values/strings.xml`
- `feature/feed/.../composeResources/values-es/strings.xml`
- `feature/feed/.../di/FeedModule.kt`
- `feature/feed/.../di/FeedModuleVerifyTest.kt` (androidHostTest)
- `feature/feed/.../presentation/feed/FeedViewModelTest.kt` (commonTest)

## Blockers / manual
- **None blocking.** MANUAL (already noted in the offline-compose chain): the real
  offline → reconnect → auto-publish behavior is the on-device check (human.md).
  This task is the UI surface; the runner/connectivity were verified by the data task.
