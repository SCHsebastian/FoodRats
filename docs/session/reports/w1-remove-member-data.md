# Report — `w1-remove-member-data`

DATA/INFRA layer for crew member removal: the real transactional Firestore write behind
`CrewRepository.removeMember`, the owner-only security rule + rules tests, and the
member-removed-notification decision. DATA LAYER ONLY (UI affordance/dialog + use case were done by
prior tasks; final UI polish is `w1-remove-member-presentation`).

## Status: DONE — both verify suites green.

## What was already on disk (verified, not changed)

- Domain task (`w1-remove-member-domain`) had wired everything *except* the actual write:
  - `CrewRepository.removeMember(crewId, requestedBy, target): Result<Unit, CrewError>` declared.
  - `FirebaseCrewRepository.removeMember` enforced the read-then-decide invariants (owner / not-self /
    member-exists) via `dataSource.fetchOnce`, then hit a `TODO(scope="w1-remove-member-data")`
    placeholder returning `CrewError.Backend.Unavailable`.
  - `CrewError.RemoveMember.{NotOwner, CannotRemoveSelf, MemberNotFound}` leaves + StringKeys + en/es
    strings + exhaustive `toStringKey()` mapper + `CrewErrorToStringKeyTest` all present and green.
  - `CrewSettingsViewModel.doRemoveMember` calls the use case and surfaces the error.

## Changes made

### 1. Datasource seam — new `removeMember`
- `feature/crew/.../data/firebase/CrewDataSource.kt`: added
  `suspend fun removeMember(crewId: CrewId, target: AccountId)` (throws `NotFoundException` /
  `NotMemberException` + mapped backend throwables).
- `feature/crew/.../data/firebase/CrewFirestoreDataSource.kt`: implemented it as a Firestore
  `runTransaction` — mirrors `leave`: re-reads the crew, throws `NotMemberException` if the target
  isn't in `memberIds` (TOCTOU guard), then `set`s `crew.copy(memberIds = memberIds - target,
  members = members - target)`. No crew-deletion branch (unlike `leave`) because the owner — who can
  never be the target — always remains, so the set is never emptied.

### 2. Repository wiring
- `feature/crew/.../data/repository/FirebaseCrewRepository.kt`: replaced the TODO with a single
  `withContext(dispatchers.io) { runCatching { dataSource.removeMember(...) } … }` (exactly ONE I/O
  boundary, matching `leave`). Error mapping: `NotFoundException → Membership.NotFound`,
  `NotMemberException → RemoveMember.MemberNotFound` (TOCTOU consistency with the pre-check), else
  `errorMapper.map(t)` → `Backend.*`. The owner/not-self/member-exists pre-checks (already present)
  are untouched.

### 3. Security rules — owner-only member removal
- `firestore.rules`, `crews/{crewId}` update block: added a 6th `||` branch:
  ```
  (resource.data.ownerId == request.auth.uid
   && request.auth.uid in request.resource.data.memberIds
   && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['memberIds', 'members'])
   && request.resource.data.memberIds.size() == resource.data.memberIds.size() - 1)
  ```
  - Only the OWNER may remove another member; the owner must remain a member in the new doc (so the
    owner can't drop self through this path); only `memberIds` + `members` may change (ownerId /
    createdAt / code / name are frozen via `hasOnly`); the set shrinks by exactly one.
  - Non-members and non-owner members are rejected — they match no update branch.
  - Owner *leaving* (dropping self) is still governed by the pre-existing leave branch (4) and is
    intentionally out of scope; the application-layer `CannotRemoveSelf` guard blocks self-removal in
    the remove-member flow.

### 4. Tests
- `feature/crew/.../data/firebase/FakeCrewDataSource.kt`: added `removeMember` override
  (`removeMemberThrows` stub + `lastRemoveMember` capture).
- `feature/crew/.../data/repository/FirebaseCrewRepositoryTest.kt`: 9 new cases — happy path
  (owner removes target, forwards args), NotFound (crew absent, no write), NotOwner (no write),
  CannotRemoveSelf (no write), MemberNotFound (target absent pre-check, no write), TOCTOU
  NotMember → RemoveMember.MemberNotFound, datasource NotFound → Membership.NotFound, PERMISSION
  → Backend.PermissionDenied, unknown → Backend.Unavailable. Added a `crewWithMembers(...)` helper.
- `firestore-tests/tests/crews.test.ts`: new `describe("crews — owner removes a member")` with 4
  cases — owner can remove another (succeeds), non-owner member cannot, remove-member path cannot
  also reassign ownerId, stranger cannot.

## Decisions

- **Removed member's meals: KEEP** (roadmap §1.5 default — explicit). No cascade-delete of the
  removed member's meals/plates in that crew; author identity continues to resolve live via
  `AccountReadPort`. Pre-launch → no migration. Destructive cascade was NOT implemented (it is not
  called for and would be irreversible).
- **Member-removed notification: SILENT — not built** (roadmap §1.5 explicit default = silent). The
  brief said to build the Cloud Function only "if §1.5/§6.3 calls for it." Roadmap §1.5 is NOT
  silent on the question — it explicitly lists `Cloud Function: notify removed member (push), or
  silent. Default: silent.` There is no real "remove-member notification" design in the 2026-05-16
  spec (its §6.3 is unrelated). So per the documented default I did NOT touch `functions/`. If
  product later wants the push, see the handoff for the build sketch.
- Removal is an atomic `set(crew.copy(...))` inside `runTransaction` (TOCTOU-safe), matching the
  `leave` pattern, rather than a Firestore `FieldValue.delete()` on the member key — keeps the DTO
  the single source of truth and re-validates membership inside the transaction.

## Verification

- `./gradlew :feature:crew:testAndroidHostTest`
  ```
  > Task :feature:crew:compileAndroidHostTest
  > Task :feature:crew:testAndroidHostTest
  BUILD SUCCESSFUL in 4s
  ```
  (One pre-existing deprecation warning in `CrewSettingsScreen.kt` re `LocalClipboardManager` — not
  this change.)
- `cd firestore-tests && pnpm test` (Firebase emulator + vitest against the production
  `firestore.rules`):
  ```
   ✓ tests/crews.test.ts (10 tests) 1123ms
   Test Files  4 passed (4)
        Tests  33 passed (33)
  ✔  Script exited successfully (code 0)
  ```
  (The `PERMISSION_DENIED` stderr lines are the expected `assertFails` rejections.)
- `functions/` NOT touched → `pnpm --dir functions test/build` not required.

## MANUAL deploy steps the user must run

Deploy the updated security rules (the new owner-only remove-member branch is INERT in prod until
deployed — until then the client write would be rejected by the old rules):

```
pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec
```

(Login is interactive: `pnpm dlx firebase-tools login`. No CI hookup for rules deploy.)

No functions deploy (none changed).

## Blockers / follow-ups

- None blocking. Presentation polish → `w1-remove-member-presentation` (see handoff).
- Optional future: member-removed push (silent today). Build sketch in the handoff if product wants it.
