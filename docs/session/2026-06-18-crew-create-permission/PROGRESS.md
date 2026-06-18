# Crew creation → "You don't have permission" (2026-06-18)

## Symptom
On the Samsung A05s (SM-A057G), signed-in user (member of crew "Minotauro 2/8")
taps **Create a crew** → enters a name → **Create** → red banner **"You don't have permission."**

Logcat confirmed it's not a UI/validation issue:
```
FR/MVI/CrewPickerViewModel intent SubmitCreate
... isCreating=false ... error=PermissionDenied ...
```
i.e. `CrewError.Backend.PermissionDenied` — a Firestore Security Rules rejection. Auth
is fine (user is signed in and already in a crew).

## Root cause — security-rules regression (commit 740e746)
`CrewFirestoreDataSource.createCrew` writes the crew doc **and** the invite-code claim
doc in ONE transaction:
```
firestore.runTransaction {
    set(newCrewRef, crewDto)                 // crews/{newId}
    set(codeRef, CrewCodeDto(crewId = ...))  // crewCodes/{code}
}
```
Commit `740e746` (2026-06-18 store-release finalize) hardened the `crewCodes` create rule:
```
&& request.auth.uid in get(/crews/$(request.resource.data.crewId)).data.memberIds
```
Security-Rules `get()`/`exists()` read **committed** state, not pending writes in the
same transaction. At create time the crew doc is still pending → `get()` returns null →
`.data.memberIds` throws → rule is false → whole transaction `PERMISSION_DENIED`.

The existing "Minotauro" crew (created 2026-05-21) predates the hardening, which is why it
exists but no NEW crew can be created. This is a total crew-creation outage in prod.

## Fix (firestore.rules, crewCodes create)
Allow the create when the crew doc does not yet exist in committed state (the atomic
crew-creation path — already guarded by the `crews/{id}` create rule: founder-only,
`memberIds.size()==1`), OR the caller is a current member (the mint-code-for-existing-crew
path the hardening was protecting). Orphan codes pointing at a never-created random
auto-id are inert (joinByCode resolves the crew doc and 404s).

## Verification — Firestore emulator rules test (/tmp/rules-test, not committed)
`@firebase/rules-unit-testing` via `firebase-tools emulators:exec`. The createCrew
transaction is reproduced as a `writeBatch` (identical rules semantics).

Fixed rules:
```
  PASS  founder creates crew + code atomically (the createCrew transaction)
  PASS  outsider cannot mint a code for an existing crew they are not in
  PASS  a current member can mint a new code for their existing crew
  PASS  unauthenticated cannot create a crew
4 passed, 0 failed
```
Old (current-prod) rules — proves the test catches the regression:
```
  FAIL  founder creates crew + code atomically (the createCrew transaction)
  PASS  outsider cannot mint a code for an existing crew they are not in
  PASS  a current member can mint a new code for their existing crew
  PASS  unauthenticated cannot create a crew
3 passed, 1 failed
```

## Status
- [x] Root-caused + reproduced on device
- [x] Fix applied to `firestore.rules` (local)
- [x] Verified against emulator (fix passes; old rules fail the create test)
- [x] **Deployed to prod** — `firebase-tools deploy --only firestore:rules --project foodrats-de4ec` → "released rules firestore.rules to cloud.firestore ✔ Deploy complete!"
- [x] **Device-verified on Samsung A05s**: created crew "TestCrewClaude" (code MQKZFR, id 6iKeQ2MuKdolWA9HE4Ac); `error=null`; user now in 2 crews; app switched active crew to the new one.
- [ ] Commit the `firestore.rules` change (deployed but uncommitted — drift risk)
- [ ] Delete the throwaway "TestCrewClaude" crew from prod (test artifact)
