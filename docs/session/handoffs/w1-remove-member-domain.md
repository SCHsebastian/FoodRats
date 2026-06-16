# Handoff → `w1-remove-member-data` (and presentation)

The domain contract for remove-member is done and green. Data task implements the real write + rule
+ (optional) notification. Presentation task owns final UI/effect polish.

## EXACT write-port method signature (already declared + called)
On `feature/crew/.../domain/repository/CrewRepository.kt`:
```kotlin
suspend fun removeMember(crewId: CrewId, requestedBy: AccountId, target: AccountId): Result<Unit, CrewError>
```
- `requestedBy` = the acting account (the use case fills this from `SessionProvider`); it is the owner.
- `target` = the member to remove.
- Returns `Result<Unit, CrewError>`.

### What the data task must do
1. `FirebaseCrewRepository.removeMember` ALREADY enforces the invariants (owner / not-self /
   member-exists, via `dataSource.fetchOnce`). Replace ONLY this line:
   ```kotlin
   // TODO(scope = "w1-remove-member-data"): ...
   return Result.failure(CrewError.Backend.Unavailable)
   ```
   with a call to a new datasource method, e.g. `dataSource.removeMember(crewId, target)`, mapped to
   `CrewError` (reuse the existing `Backend.*` mapping the other writes use). Keep exactly one
   `withContext(dispatchers.io)` boundary (the repo write methods already follow this — match
   `deleteCrew`/`setBlindVoting`).
2. Add `removeMember` to `CrewDataSource` + implement in `CrewFirestoreDataSource`: transactional /
   atomic delete of `crews/{crewId}.members.{target}` (member docs are a map keyed by uid, per the
   existing `MemberDto`/`CrewMapper` shape — confirm against `CrewDto`).
3. `firestore.rules`: owner-only member removal; owner CANNOT remove self (enforce both server-side —
   the client checks are advisory). Add a rule test.

## New error leaves (use these, don't reintroduce `NotImplemented`)
`feature/crew/.../domain/error/CrewError.kt`:
```kotlin
sealed interface RemoveMember : CrewError {
    data object NotOwner : RemoveMember
    data object CannotRemoveSelf : RemoveMember
    data object MemberNotFound : RemoveMember
}
```
- The old `CrewError.NotImplemented.RemoveMember` leaf and `CrewStringKey.RemoveMemberNotYetAvailable`
  + string `crew_remove_member_not_yet_available` are GONE. Don't resurrect them.
- StringKeys/strings already exist: `ErrorRemoveMemberNotOwner` / `ErrorRemoveMemberCannotRemoveSelf`
  / `ErrorRemoveMemberMemberNotFound` (EN + ES). The `toStringKey()` mapper is exhaustive and locked
  by `CrewErrorToStringKeyTest`.
- Backend/network failures from the write should map to the existing `CrewError.Backend.*` arm
  (already wired in the mapper), NOT a new leaf.

## Member-removed notification (design §6.3; roadmap §1.5 default = SILENT)
NOT built in any wave-1 task yet. §6.3 says a member-removed notification is part of the design, but
roadmap §1.5's default decision is **silent** (no push). If product wants the push:
- Add a Cloud Function (mirror the existing `functions/src/triggers/*` + `functions/src/fcm/push.ts`
  pattern) that fires on the member-doc deletion and sends a push to the removed member.
- It should be a "just open the app" notification (no deep link) per the navigation convention —
  reminders without a `data.link` open Feed/launcher (see CLAUDE.md "Navigation audit fixes").
- Append it as a MANUAL/deferred item if not building it.

## Open product decision to confirm (roadmap §1.5)
Keep vs. delete the removed member's meals in that crew. **Default: keep** (their plates stay;
author identity resolves live via `AccountReadPort`). No migration needed (pre-launch).

## Presentation task notes
- `CrewSettingsViewModel.doRemoveMember` now calls `removeMember(crewId, intent.accountId)` and
  surfaces `r.error` on failure (no success effect yet). Final UI (success toast/snackbar, optimistic
  removal, disabling the row mid-call) is for `w1-remove-member-presentation`.
- `CrewSettingsViewModelTest.owner_confirms_remove_member_removes_member_without_error` is the current
  happy-path coverage; extend with the failure-path UI assertions when you add the effects.
