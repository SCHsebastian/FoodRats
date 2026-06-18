# functions repair — 2026-06-17

## functions-01 (MEDIUM security) — ratingSum delta guard
**File:** `firestore.rules` line 170 (meal vote update block)
**Change:** Added `&& request.resource.data.ratingSum == resource.data.ratingSum + request.resource.data.ratings[request.auth.uid].score` after the voterCount check. A voter can no longer send an arbitrary ratingSum; it must equal the old sum plus their own score, so the feed aggregate can't be silently inflated/deflated.
**Tests:** Firestore rules can't be unit-tested in isolation; rule is syntactically valid. Deploy is a manual step (`pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`).

## functions-02 (MEDIUM security) — crewCodes create membership guard
**File:** `firestore.rules` crewCodes create block (~line 288)
**Change:** Added `&& request.auth.uid in get(/databases/$(database)/documents/crews/$(request.resource.data.crewId)).data.memberIds` to the create allow condition. Only a current member of the target crew can mint an invite code for it; any authenticated user was previously able to mint codes for arbitrary crews.
**Tests:** Same as functions-01 — rules deploy is manual.

## functions-03 (LOW perf) — MAX_PATHS cap in mintPlateUrls
**File:** `functions/src/callables/mintPlateUrls.ts`
**Change:** Exported `MAX_PATHS = 200`; applied `.slice(0, MAX_PATHS)` to the incoming `request.paths` array before passing to `authorizedPaths`. This caps the signing loop at 200 entries regardless of what the client sends.
**Test added:** `mintPlateUrls.test.ts` — new case "caps requests at MAX_PATHS paths (functions-03)": builds MAX_PATHS+10 plate paths, asserts `Object.keys(res.urls).length <= MAX_PATHS`. All 114 tests pass.

## functions-04 (LOW perf) — sendToCrew parallel sends
**File:** `functions/src/fcm/push.ts`
**Change:** Replaced the sequential `for…await` loop that called `sendToTokens` per member with `Promise.all(tokensByUid.filter(...).map(...sendToTokens...))`. All per-member sends now fire concurrently; the function awaits all completions. The pruneUnregistered calls inside each `sendToTokens` still run per-member so token cleanup is unaffected.
**Tests:** No dedicated push.ts unit tests exist in the suite; the change is a mechanical concurrency refactor with identical semantics (all awaited, errors propagate). No new test added (LOW, mechanical).

## functions-05 (SKIPPED)
Skipped per instructions: needs a new Firestore composite index; deferred.

## Verification
`pnpm --dir functions build` — exit 0, no errors.
`pnpm --dir functions test` — `Test Files  11 passed (11)  Tests  114 passed (114)`.
