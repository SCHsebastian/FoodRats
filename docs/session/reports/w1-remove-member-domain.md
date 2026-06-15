# Report — `w1-remove-member-domain`

**Status:** DONE (domain layer). Verified green.

## Goal
Replace the `RemoveMemberUseCase` stub (always returned `CrewError.NotImplemented.RemoveMember`)
with a real owner-only / not-self / member-exists use case + a proper error tree + the domain write
port it delegates to. Domain layer only — the Firestore write + security rule + push are separate
tasks (`w1-remove-member-data`).

## Prior work
None. No `docs/session/reports/` or `handoffs/` entry existed; the only `RemoveMember*` source files
were the stub + its "always not implemented" test. Implemented fresh.

## What changed

### Domain
- **`feature/crew/.../domain/error/CrewError.kt`** — removed the `NotImplemented` group (and its
  `RemoveMember` leaf) entirely; added a dedicated group:
  ```kotlin
  sealed interface RemoveMember : CrewError {
      data object NotOwner : RemoveMember
      data object CannotRemoveSelf : RemoveMember
      data object MemberNotFound : RemoveMember
  }
  ```
  Backend/transport failures reuse the existing `CrewError.Backend.*` arm (consistent with every
  other write use case — no new backend leaf needed).
- **`feature/crew/.../domain/repository/CrewRepository.kt`** — new write-port method:
  ```kotlin
  suspend fun removeMember(crewId: CrewId, requestedBy: AccountId, target: AccountId): Result<Unit, CrewError>
  ```
  Mirrors `deleteCrew`/`setBlindVoting`/`renameCrew` (owner-enforcing write methods take
  `requestedBy`). Chose `CrewRepository.removeMember(...)` over a new `CrewMemberWritePort` because
  every existing crew write already lives on `CrewRepository`; a separate port would be inconsistent.
- **`feature/crew/.../domain/usecase/RemoveMemberUseCase.kt`** — real logic. Resolves the requester
  from `SessionProvider.requireCurrent()` (→ `Backend.Unavailable` if unauthenticated, same pattern
  as `DeleteCrewUseCase`), fetches the crew via `repository.observeCrew(crewId).first()`, then
  enforces in-domain: owner check → `RemoveMember.NotOwner`; self check → `RemoveMember.CannotRemoveSelf`;
  membership check → `RemoveMember.MemberNotFound`; only then delegates to `repository.removeMember`.
  Signature: `suspend operator fun invoke(crewId: CrewId, target: AccountId): Result<Unit, CrewError>`
  (caller supplies crew + target; requester comes from session).

### Presentation (minimal, to keep it compiling — final polish is `w1-remove-member-presentation`)
- **`CrewSettingsViewModel.kt`** — call site updated `removeMember(intent.accountId)` →
  `removeMember(crewId, intent.accountId)`.
- **`CrewErrorToStringKey.kt`** — replaced the `NotImplemented.RemoveMember` arm with the three new
  leaves: `NotOwner → ErrorRemoveMemberNotOwner`, `CannotRemoveSelf → ErrorRemoveMemberCannotRemoveSelf`,
  `MemberNotFound → ErrorRemoveMemberMemberNotFound`. The `when` stays exhaustive.
- **`CrewStringKey.kt`** — dropped `RemoveMemberNotYetAvailable`; added the three new keys.
- **`values/strings.xml` + `values-es/strings.xml`** — dropped `crew_remove_member_not_yet_available`;
  added `crew_error_remove_member_not_owner` / `_cannot_remove_self` / `_member_not_found` in EN + ES.
  (Error copy is final, not placeholder.)

### Data (compile-only seam — real write is `w1-remove-member-data`)
- **`FirebaseCrewRepository.kt`** — added `removeMember` override that enforces the same invariants
  (via `fetchOnce`) and then **returns `CrewError.Backend.Unavailable` with a `TODO(scope =
  "w1-remove-member-data")`** for the actual Firestore member-doc deletion. This keeps the module
  compiling without doing the write. The data task replaces only that final line + adds the
  datasource method.

### Tests
- **`FakeCrewRepository.kt`** — added a real `removeMember` override (owner/self/membership checks +
  member mutation) plus `nextRemoveMember` override hook and `lastRemoveMember: Triple<CrewId,
  AccountId, AccountId>` recorder, mirroring the existing fakes.
- **`RemoveMemberUseCaseTest.kt`** — rewritten: owner removes member → port called + member gone;
  non-owner → `NotOwner`; owner removing self → `CannotRemoveSelf`; unknown member → `MemberNotFound`.
- **`CrewErrorToStringKeyTest.kt`** — replaced the not-implemented case with three asserts (locks
  exhaustiveness over the new leaves).
- **`CrewSettingsViewModelTest.kt`** — `buildVm` now wires `RemoveMemberUseCase(repo, session)`; the
  stale "surfaces not implemented" test became `owner_confirms_remove_member_removes_member_without_error`
  (asserts no error + `repo.lastRemoveMember == (crewId, ownerId, memberId)`).

## Decisions
- **Error leaves:** dedicated `CrewError.RemoveMember.{NotOwner,CannotRemoveSelf,MemberNotFound}`
  (not reusing `Authorization.NotOwner`/`Membership.NotMember`) — the task asked for them explicitly
  and they read clearer at the call site. Backend failures reuse `CrewError.Backend.*` (no new arm).
- **Write port shape:** method on `CrewRepository`, not a new `CrewMemberWritePort` — consistency
  with all existing crew writes.
- **Invariants enforced in BOTH the use case AND `FirebaseCrewRepository`** — the use-case checks are
  the testable domain contract; the repo re-checks before the (future) write. The data task should
  additionally enforce them in `firestore.rules` (authoritative, atomic).
- **`removeMember(crewId, target)` use-case signature** — requester is implicit from session, exactly
  like `DeleteCrewUseCase(crewId)` / `SetBlindVotingUseCase(crewId, enabled)`.

## Verify
```
./gradlew :feature:crew:testAndroidHostTest
```
```
> Task :feature:crew:testAndroidHostTest
BUILD SUCCESSFUL in 3s
90 actionable tasks: 20 executed, 70 up-to-date
```
Per-class result XML: `RemoveMemberUseCaseTest` tests=4 failures=0; `CrewErrorToStringKeyTest`
tests=20 failures=0; `CrewSettingsViewModelTest` tests=13 failures=0.

## Blockers
None.

## For the data task (`w1-remove-member-data`) — see handoff
- Implement the Firestore transactional member removal in `CrewDataSource`/`CrewFirestoreDataSource`
  and replace the `TODO` line in `FirebaseCrewRepository.removeMember` (the invariant checks already
  exist there).
- `firestore.rules`: owner-only member removal, owner can't remove self.
- **Decision (roadmap §1.5, default — confirm):** keep the removed member's meals in the crew
  (identity resolves live via `AccountReadPort`).
- **Member-removed notification (design §6.3):** Cloud Function to notify the removed member (push) —
  roadmap §1.5 default is **silent**. Not built here. See handoff.
