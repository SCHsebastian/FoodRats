# Handoff → `w1-remove-member-presentation`

Data/infra for remove-member is DONE and green. The real transactional write, the owner-only
security rule + rules tests, and the keep-meals / silent-notification decisions are all in.

## What is fully wired (do NOT re-do)

- `CrewRepository.removeMember(crewId, requestedBy, target): Result<Unit, CrewError>` — real write.
  - Owner-only / not-self / member-exists invariants enforced in `FirebaseCrewRepository` (read-then-
    decide) AND server-side in `firestore.rules` (the application checks are advisory; the rule is the
    authority). Atomic membership shrink via `CrewFirestoreDataSource.removeMember` (a `runTransaction`
    mirroring `leave`).
- Error leaves `CrewError.RemoveMember.{NotOwner, CannotRemoveSelf, MemberNotFound}` + StringKeys +
  en/es strings + exhaustive `toStringKey()` mapper (locked by `CrewErrorToStringKeyTest`). Backend
  failures map to `CrewError.Backend.*`.
- `RemoveMemberUseCase` → `CrewSettingsViewModel.doRemoveMember(...)` calls `removeMember(...)` and
  surfaces `r.error` on failure via the existing error banner. The owner-only "Remove" row affordance
  + the confirm `AlertDialog` already exist in `CrewSettingsScreen`.
- Tests green: `FirebaseCrewRepositoryTest` (9 new remove-member cases), `crews.test.ts` (4 new rules
  cases). Verify: `./gradlew :feature:crew:testAndroidHostTest`; rules: `cd firestore-tests && pnpm test`.

## What presentation polish REMAINS (your task)

The current flow has NO success feedback and NO mid-call UX. From the domain handoff + spec §6.3:
- Success effect: a toast/snackbar (`CrewSettingsContract` effect) confirming the member was removed
  — needs a new `CrewStringKey` + en/es string (e.g. `MemberRemoved`).
- Optimistic removal of the row and/or disabling the row + a spinner while the call is in flight (the
  VM should reflect a per-target "removing" state in `State` — single-source-of-truth, no parallel
  `MutableStateFlow`).
- Extend `CrewSettingsViewModelTest`: the failure-path UI assertions (error banner shows the right
  `StringKey` for each `RemoveMember.*` leaf) and the success-effect assertion. The current happy-path
  coverage is `owner_confirms_remove_member_removes_member_without_error`.

## Client push-localization follow-up (only if product later wants the notification)

The member-removed push is SILENT by roadmap §1.5 default — NOT built. If product reverses that:
- SERVER: add a Cloud Function mirroring `functions/src/triggers/*` + `functions/src/fcm/push.ts`,
  firing on the `crews/{id}` `onUpdate` when a uid is dropped from `members` by the owner (compare
  before/after `memberIds`), sending a "just open the app" push (NO `data.link` → opens Feed/launcher,
  per the navigation convention) to the removed uid's device tokens. Add a vitest test +
  `pnpm --dir functions test && build`.
- CLIENT: localized copy for the push title/body is a follow-up (mirror the streak-nudge i18n path —
  `NotificationStringKey` + en/es). Not needed while the notification is silent.

## Deploy step the user must run before this ships

`pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec` — the new owner-only
remove-member rule branch is inert in prod until deployed; the client write fails on the old rules
until then.
