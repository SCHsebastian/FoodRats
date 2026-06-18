# Meal plate images uploaded then vanish — root cause + fix

## Symptom
User: "images are not being uploaded correctly … if they're being uploaded, I can not see them."

## Verified facts (prod foodrats-de4ec, 2026-06-18)
- All Cloud Functions ACTIVE (mintPlateUrls, onPlateImageFinalized, …); IAM Token Creator
  granted on the Gen-2 compute SA. Coil+Ktor image loader installed on both platforms. Deployed
  firestore.rules == local. So the display pipeline + backend are healthy.
- For both crews' 2026-06-18 lunch: Firestore meal DOC EXISTS (platePath set) but the full
  `{mealId}.jpg` is MISSING from Storage; for crew l0wU the `_thumb.jpg` EXISTS (made 14:33:09).
- onPlateImageFinalized logs: a 404 "No such object" for one crew, and a "no meal … skipping"
  + a second finalize ~1s later for the other ⇒ the plate was uploaded TWICE per publish.

## Root cause (code)
1. `BackgroundMealUploadCoordinator.enqueueDraftUpload` runs TWO concurrent publishes of the
   same draft: the immediate `doUpload()` AND `draftQueue.enqueue()` → `DraftRetryRunner` drain →
   `publish()`. (Comment called the race "a harmless overwrite" — it is not.)
2. `MealFirestore.write` is `.set(dto)`, which OVERWRITES — it never throws ALREADY_EXISTS.
   Same-slot uniqueness is enforced ONLY by the create rule's `!exists(...)`, whose rejection
   surfaces as PERMISSION_DENIED.
3. `FirebaseMealRepository.publish` catch treats only `FirebaseFault.AlreadyExists` as benign;
   the loser's PERMISSION_DENIED falls into `else` → `storage.delete()` deletes the plate that
   backs the WINNER's already-live doc. Thumb survives (delete only removes `{mealId}.jpg`).

## Fix
- `FirebaseMealRepository.publish`: on a write fault, only reclaim the blob as an orphan when NO
  live doc exists (`mealExists` recheck); otherwise it's a benign duplicate (AlreadyPostedToday),
  never delete. [data-loss fix]
- `DraftRetryRunner.attempt` + `BackgroundMealUploadCoordinator.doUpload`: treat AlreadyPostedToday
  as idempotent SUCCESS (remove entry / clear pending flag) so the double-fire loser doesn't show a
  spurious "failed upload" or spin a WorkManager retry loop.
- Test fakes + regression test locking the no-delete-on-existing-doc behavior.

## Status: DONE (track 1 data-loss fix + track 2 single-executor collapse) — meal 180/0/0, feed 105/0/0, assembleDebug green; iOS framework link green. Client-only; needs a new build on device. Not committed (awaiting user).
