# Meal plate images uploaded then vanish — root cause, fix, verification

## TL;DR
Every meal publish fires TWO concurrent `publish()` calls for the same deterministic meal id.
The loser's Firestore `.set()` is rejected by the create rule's `!exists(...)` as PERMISSION_DENIED;
the repository misclassified that as a real failure and ran orphan-cleanup, **deleting the full
plate image that backs the winner's already-live meal doc**. Fixed client-side. No backend change.

## Evidence (prod foodrats-de4ec, 2026-06-18)
- Backend healthy: all functions ACTIVE; IAM Service-Account-Token-Creator granted on the Gen-2
  compute SA (`475840003160-compute@`); deployed firestore.rules == repo; Coil+Ktor loader installed
  on both platforms. So display + signed-URL minting were never the problem.
- Both crews' 2026-06-18 lunch: Firestore meal DOC EXISTS (platePath set) but the full
  `{mealId}.jpg` is MISSING from Storage. For crew l0wU the `_thumb.jpg` EXISTS (made 14:33:09).
- `onPlateImageFinalized` logs: a 404 "No such object" for one crew, and "no meal … skipping"
  followed by a SECOND finalize ~1s later for the other ⇒ the plate was uploaded TWICE per publish.
- The surviving thumb + missing full proves the deleter was the client orphan-cleanup
  (`PlateStorageDataSource.delete` removes only `{mealId}.jpg`, never `_thumb.jpg`);
  `onMealDeleted` removes BOTH, so it was not involved.

## Root cause (three compounding facts)
1. `BackgroundMealUploadCoordinator.enqueueDraftUpload` runs the immediate `doUpload()` AND
   `draftQueue.enqueue()` → `DraftRetryRunner` drain → `publish()`. Two concurrent publishes of the
   same draft. (Old comment claimed the race was "a harmless overwrite" — it was not.)
2. `MealFirestore.write` = `.set(dto)`, which OVERWRITES — it never throws ALREADY_EXISTS. Same-slot
   uniqueness is enforced ONLY by the create rule `!exists(...)`, whose rejection is PERMISSION_DENIED.
3. `FirebaseMealRepository.publish` catch treated only `FirebaseFault.AlreadyExists` as benign; the
   loser's PERMISSION_DENIED fell into `else` → `storage.delete()` deleted the live plate.

Both racers pass the `mealExists` free-slot pre-check (doc not yet visible), both upload (overwriting
the deterministic blob), winner's `.set()` creates the doc, loser's `.set()` is rejected → deletes it.

## Fix (client-only, commonMain)
- `FirebaseMealRepository.publish` catch: on a write fault, reclaim the blob ONLY when NO live doc
  exists (`mealExists` recheck; inconclusive ⇒ keep). Otherwise it's a benign duplicate →
  AlreadyPostedToday, never delete.
- `DraftRetryRunner.attempt` + `BackgroundMealUploadCoordinator.doUpload`: treat AlreadyPostedToday
  as idempotency-SUCCESS (remove queue entry / clear pending flag) so the double-fire loser no longer
  shows a phantom "failed upload" in the feed bar or spins a WorkManager retry loop.
- Fixed the misleading "harmless overwrite" comment.
- Tests: `FakeMealFirestore.existingSlotsAfterWriteFault` knob; regression tests
  `FirebaseMealRepositoryTest.publish_write_rejected_but_doc_now_exists_keeps_plate_and_reports_already_posted`
  and `DraftRetryRunnerTest.pending_then_already_posted_today_removes_the_entry`.

## Verification
- `./gradlew :feature:meal:testAndroidHostTest` → BUILD SUCCESSFUL; suite tests=179 failures=0 errors=0
  (incl. both new tests AND the two pre-existing cleanup tests still green — backward-compatible).
- `./gradlew :feature:feed:testAndroidHostTest` → BUILD SUCCESSFUL.
- `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

## Not fixed here (noted)
- The double-publish is now harmless but still WASTES one redundant upload + classify per publish.
  Future optimization: make the durable queue the single publish executor when bound (today doUpload
  drives the composer/feed `Uploading` status, so disentangling needs care).
- Already-broken historical meals (e.g. 2026-06-18 lunch) cannot be recovered — the full plate bytes
  are gone. Crew l0wU's lunch will still show its surviving thumbnail in the feed but be blank on the
  detail hero. Remedy: delete + republish those meals.

---

## Follow-up: collapsed the double-publish to a single executor (requested)

The data-loss fix above made the double-publish harmless; this removes it. The durable queue +
`DraftRetryRunner` (roadmap §5.2) is the intended primary mechanism; the in-process `doUpload`
fast-path was a redundant second publisher bolted on top (and the WorkManager worker ran BOTH paths
too).

### Change — durable queue is the single executor (when bound)
- `BackgroundMealUploadCoordinator`:
  - `enqueueDraftUpload`: in durable mode, ONLY `draftQueue.enqueue(draft)` + `scheduler.schedule()`
    — no parallel `doUpload()`. The durable entry (incl. plate bytes) is itself the process-death /
    offline safety net, so the legacy `Keys.MealUploadPending` flag + in-process publish are unused.
  - `init`: durable mode mirrors the queue into `_status` (`deriveStatus`: active→Uploading,
    terminal-failed→Failed, else Idle) so Feed's "uploading" indicator still works without this
    coordinator publishing; `retryRunner.start` handles launch + connectivity resume.
  - `resumeFromBackgroundWorker`: no-op `true` in durable mode (the worker drains via
    `DraftRetryRunner.runOnce`); the legacy in-process path remains only for the no-queue fallback.
  - `doUpload` / single-flag path KEPT as the fallback for when `draftQueue == null` (unit tests).
- `DraftRetryRunner` now emits the true publish-outcome analytics (relocated from the coordinator):
  `meal_published` on a successful drain, `meal_publish_failed` only on a TERMINAL failure (no
  per-retry over-counting). Injected `analytics: AnalyticsPort = NoopAnalyticsTracker` (default keeps
  direct-construction tests green); wired `analytics = get()` in `MealModule`.
- `MealUploadWorker` doc updated: in durable mode the queue drain is the only publisher.

### Result
One publish per draft (deterministic `MealId`). No redundant upload. No phantom failed-upload in the
feed bar. Process-death / offline / connectivity-resume all handled by the single durable mechanism.

### Verification (quoted)
- `:feature:meal:testAndroidHostTest` → BUILD SUCCESSFUL; meal tests=180 failures=0 errors=0
  (incl. MealModuleVerifyTest — DI still resolves — and the new
  `DraftRetryRunnerTest.successful_drain_emits_meal_published_analytics`).
- `:feature:feed:testAndroidHostTest` → tests=105 failures=0 errors=0.
- `:androidApp:assembleDebug` → BUILD SUCCESSFUL.
- `:shared:linkDebugFrameworkIosSimulatorArm64` → (see PROGRESS for result).
