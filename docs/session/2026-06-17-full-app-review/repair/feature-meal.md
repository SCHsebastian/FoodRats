# feature-meal repair report (2026-06-17)

## meal-01 (MEDIUM correctness) — DraftQueueLocalStore RMW atomicity
- Added a per-instance `kotlinx.coroutines.sync.Mutex` (`mutateLock`) and wrapped the entire `mutate()` read-modify-write in `mutateLock.withLock { }`. A single DataStore `set` is atomic, but the `read()`→`set()` span in `mutate` is not; two concurrent mutations (enqueue racing a retry status flip) could each read the same snapshot and the later write would clobber the earlier (lost update). The mutex serializes the whole RMW so writes compose.
- Replaced the false "the underlying DataStore edit is itself atomic, so a concurrent read/modify/write is serialized by DataStore" KDoc with an accurate description of the lost-update window and the mutex fix.
- Test added: `DraftQueueLocalStoreTest.concurrent_adds_do_not_lose_updates` — drives 8 concurrent `add()`s through a `YieldingDataStore` whose read+write each `yield()` to open the interleave window, then asserts all 8 entries survive. Without the mutex the later writes clobber earlier ones; with it they all persist.

## meal-04 (LOW cleanup) — deduplicate uploadErrorKey()
- Removed the duplicate `private fun MealError.uploadErrorKey()` from `BackgroundMealUploadCoordinator.kt`. The identical `internal fun MealError.uploadErrorKey()` already lived in `DraftRetryRunner.kt` (module-local) — kept that as the single source of truth and imported it into the coordinator (`import ...data.queue.uploadErrorKey`).
- Removed the now-unused `MealError` import from the coordinator.
- Updated the surviving helper's KDoc (it no longer "mirrors" a private copy; it is THE shared mapper for both the queue retry path and the single-upload fast path).
- No core/domain typed key introduced (per instruction; pairs with core-domain-04 which is owned by another agent).

## meal-05 (LOW perf) — ComposePlateScreen LaunchedEffect key
- Changed `LaunchedEffect(state.photoBytes)` to `LaunchedEffect(state.photoBytes?.contentHashCode())` so the classification kick-off keys on content, not ByteArray identity (recomposition can hand an equal-but-distinct array). Comment updated.

## meal-03 (MEDIUM correctness/UX) — surface CaptureMealViewModel failure arms
- Followed the module's existing MVI **state error field** pattern (mirrors `ComposePlateState.error` + `FrErrorBanner`), not a one-shot effect — NavGraph is owned by `:shared` and out of scope, so an effect arm would need a wiring change I can't make. The state field surfaces inside the screen without touching the public callback API.
- `CaptureMealState` gained `error: MealStringKey? = null`.
- `CaptureMealViewModel`: replaced all 4 `println` swallows with `update { it.copy(error = ...) }`:
  - session `requireCurrent()` error → `CaptureSessionError`
  - empty crew set → `CaptureNoCrews`
  - `startDraft` `Result.Err` → `CaptureDraftFailed`
  - `updateDraft` (PhotoTaken) `Result.Err` → `CapturePhotoFailed`
  - Also clears `error` on a successful Start and at the top of PhotoTaken.
- `CaptureMealScreen` now collects state and renders an `FrErrorBanner` inside an `FrScreenScaffold` when `state.error != null`. Public callbacks (`onCaptured`/`onCancelled`/`onOpenSettings`) unchanged.
- Added 4 `MealStringKey` entries (`CaptureSessionError`, `CaptureNoCrews`, `CaptureDraftFailed`, `CapturePhotoFailed`) + matching `meal_capture_*` strings in en + es `strings.xml`.
- Test added: `CaptureMealViewModelTest` — covers the session-error arm, the no-crews arm, and the happy path (no error). Uses the module's `FakeMealRepository` + local `FakeSessionProvider`/`FakeCrewMembership`, Turbine `expectMostRecentItem()` under `UnconfinedTestDispatcher` (module pattern).

## Skipped
- meal-02 (NoDraftFound leaf) and meal-06 (FirebaseFault storage match) — deferred per instructions; not touched.

## Build risk to watch
- `MealStringKey` is an `enum`, not an error mapper, so the new entries don't break any `*ErrorToStringKey` exhaustiveness test. The new strings use generated `Res.string.meal_capture_*` symbols — these are codegen-resolved at build; if a clean build hasn't regenerated resources, the `MealStringKey` imports will appear unresolved until the compose-resources task runs (normal).
- No public API / signature changes. `CaptureMealState` gained a defaulted field only.
- The meal-01 concurrency test relies on `StandardTestDispatcher` + explicit `yield()` in the fake store to force interleave; deterministic, no real-time delays.
