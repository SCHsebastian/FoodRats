# Report — `w0-account-deletion-presentation`

**Date:** 2026-06-14
**Scope:** Account deletion PRESENTATION layer — the `ProfileViewModel.doDeleteAccount()` Ok-branch
finish sequence, the `analytics` injection + explicit Koin binding, the repurposed ownership i18n
copy, and a `ProfileViewModelTest`. The Settings entry, `DeleteAccountScreen`, confirmation dialog,
phrase gate, `DeleteMyAccountUseCase`, error tree/mapper, and the `AccountDeleted` taxonomy leaf were
ALREADY built and shipping (per spec §8 and the domain/data tasks) — left untouched.

Spec: `docs/specs/2026-06-14-account-deletion-design.md` §4.3, §7, §8, §9, §13, §14.2.
Handoffs read: `docs/session/handoffs/w0-account-deletion-data.md`,
`docs/session/handoffs/w0-account-deletion-domain.md`; data report.

## Prior work check

The client UI was already on disk and complete: `DeleteAccountScreen.kt` (warning header,
consequences checklist, phrase gate via `FrTextField`, `FrButton(variant = Danger)`,
`FrConfirmDialog(destructive = true)`), the Profile Danger-Zone entry, all `AuthStringKey.DeleteAccount*`
keys + en/es strings, `ProfileViewModel`'s delete state/intents, `DeleteMyAccountUseCase`,
`ProfileError.Delete` tree + `ProfileErrorToStringKey` mapper (already updated by the domain task with
`OwnerReassignFailed → DeleteAccountErrorOwnership`), and `ProfileErrorToStringKeyTest` (already
exhaustive). The data task had already swapped in `FirebaseAccountDeletionPort` + binding. The ONLY
gaps were the Ok-branch teardown wiring, the `analytics` ctor param + explicit Koin binding, the stale
ownership copy, and the missing `ProfileViewModelTest`. No partial presentation work to resume.

## What changed

### `:feature:auth` — `ProfileViewModel.kt`
- Added ctor param `analytics: AnalyticsPort = NoopAnalyticsTracker` (default keeps the existing
  graph/tests green) + imports (`AnalyticsEvent`, `AnalyticsPort`, `NoopAnalyticsTracker`).
- Rewrote the `doDeleteAccount()` `Ok` branch to run the EXACT teardown order (spec §7 / data handoff):
  1. `analytics.track(AnalyticsEvent.AccountDeleted)` — fired AFTER the use case returns `Ok`, BEFORE
     the identity is cleared, so it still attributes to the about-to-be-deleted user. Only call site.
  2. `analytics.setUserId(null)`
  3. `analytics.resetData()`
  4. `signOut.signOut()` — local teardown (Auth user already deleted server-side); root-nav navigates
     to SignIn on its own when `SessionProvider.current` goes signed-out. No navigation effect added.
  Then clears UI state (`isDeletingAccount = false`, `deleteScreenOpen = false`, `deleteConfirmation = ""`).
  The `Err` branch fires NOTHING (no event, no identity reset, no sign-out) and only sets `deleteError`
  + keeps the screen open so the user can retry with a still-valid session. MVI single-source-of-truth
  preserved (state only via `update { it.copy(...) }`); no `withContext` in the VM.

### `:feature:auth` — `di/AuthModule.kt`
- Switched the `ProfileViewModel` binding from `viewModelOf(::ProfileViewModel)` to an EXPLICIT
  `viewModel { ProfileViewModel(..., analytics = get()) }` (all 15 deps named) so the
  `NoopAnalyticsTracker` default cannot short-circuit graph resolution and the real consent-gated
  tracker is injected — per the analytics-base convention. `viewModelOf` import is still in use by
  `TopBarAvatarViewModel`, so no import change. `AnalyticsPort::class` was already in
  `AuthModuleVerifyTest.extraTypes` (no test edit needed there).

### `:feature:auth` — i18n (spec §9)
- Repurposed `auth_delete_account_error_ownership` copy in BOTH locales (the key
  `AuthStringKey.DeleteAccountErrorOwnership` maps `Delete.OwnerReassignFailed`):
  - en: `Couldn't reassign your crew's owner. Please try again.`
  - es: `No se pudo reasignar la propiedad de tu grupo. Inténtalo de nuevo.`
  Was the old "Transfer ownership of your crews before deleting" message. `…_not_implemented` left as
  dead-but-kept (one release), per §9.

### `:feature:auth` — `commonTest/.../profile/ProfileViewModelTest.kt` (NEW)
- 2 cases, `UnconfinedTestDispatcher` + Turbine, `expectMostRecentItem()` per the MVI test pattern.
  Inline fakes for the preference/permission ports + a `RecordingSignOutPort` + `FakeAccountDeletionPort`;
  reused `RecordingAnalyticsTracker` (commonMain), `FixedSessionProvider`, `FakeAccountWritePort`.
  - `ok_runs_finish_sequence_event_then_clear_identity_then_sign_out` — asserts `events == [AccountDeleted]`,
    `userIds == [null]`, `resetCount == 1`, `signOutCount == 1`, and state cleared (screen closed,
    confirmation emptied, no error).
  - `err_sets_error_and_fires_nothing` — port returns `Deletion.OwnerReassignFailed`; asserts
    `deleteError == DeleteAccountErrorOwnership`, screen stays open, and analytics/sign-out are ALL
    untouched (empty events/userIds, `resetCount == 0`, `signOutCount == 0`).

No `Fr*` added to `:core:designsystem`, so no catalog entry needed (the delete UI is feature-local and
was pre-existing anyway). No `shared` nav touched.

## Decisions
- `signOut.signOut()`'s `Result` is intentionally not surfaced on the delete path: the data handoff/spec
  §7 state it's local cleanup that succeeds regardless of the (already-deleted) remote user, and the
  delete UI is being torn down — there is no banner to show a local-signout error to. (The Profile
  sign-out *button* still surfaces its own error via `doSignOut()`, unchanged.)
- Typed the `assertEquals(listOf<AnalyticsEvent>(...))` literal explicitly — `AccountDeleted` is a
  `data object` whose inferred element type is its own singleton type, which fails `assertEquals`
  inference against `MutableList<AnalyticsEvent>`.

## Verification

`./gradlew :feature:auth:testAndroidHostTest` (last 3 lines):
```
> Task :feature:auth:testAndroidHostTest

BUILD SUCCESSFUL in 2s
```
Per-suite (from `build/test-results/testAndroidHostTest/`):
```
ProfileViewModelTest      tests="2" skipped="0" failures="0" errors="0"
AuthModuleVerifyTest      tests="1" skipped="0" failures="0" errors="0"   (Koin graph — new explicit ProfileViewModel binding resolves)
```
`:shared:testAndroidHostTest` NOT run — no `shared` nav was touched (root-nav already handles the
signed-out → SignIn transition; this task added no nav effect).

## MANUAL — deploy/store steps (carried from the data task; not codeable here)

1. `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec` (then `firestore:indexes`
   if `FAILED_PRECONDITION: index required` surfaces for `authorId` collection-group queries, then
   `firestore:rules` no-op) — order per `docs/cicd-runbook.md` §6.
2. Confirm the Functions runtime SA has `firebaseauth.users.delete`.
3. App Store Connect (App Privacy → Account Deletion) + Google Play Console (Data safety → account-deletion
   declaration) + a public deletion-request URL.
4. On-device smoke: sign in → publish meal → delete account → SignIn lands; re-sign-in makes a fresh
   `accounts/{uid}`.

Also pending (one-release cleanup, not this task): remove the now-dead `Delete.NotImplemented` leaf +
`DeleteAccountErrorNotImplemented` key/strings in a follow-up; add the §364 "Account deletion" entry to
the root `CLAUDE.md` once the whole feature is deployed and smoke-verified.

## Blockers

None.
