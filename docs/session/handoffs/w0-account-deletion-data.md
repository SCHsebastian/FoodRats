# Handoff — `w0-account-deletion-data` → `w0-account-deletion-presentation`

The backend is live and bound. The real `FirebaseAccountDeletionPort` (calls the `deleteAccount`
Cloud Function in `europe-west3`) replaces the stub and is wired in `authModule`. The presentation
task only needs to finish the `ProfileViewModel` delete-flow teardown — nothing in data/domain
needs further change.

## What's already done (do NOT redo)

- `FirebaseAccountDeletionPort` exists (`feature/auth/.../data/firebase/`) + Koin binding swapped:
  `single<AccountDeletionPort> { FirebaseAccountDeletionPort(dispatchers = get()) }`.
  `StubAccountDeletionPort` deleted. `libs.firebase.functions` added to `:feature:auth`.
- `deleteAccount` Cloud Function + vitest done and green; exported from `functions/src/index.ts`.

## What the presentation task must call

`DeleteMyAccountUseCase` already calls `AccountDeletionPort.requestDeletion(accountId, confirmation)`
(unchanged surface). Results the UI maps via the existing `ProfileError.Delete` tree:

| Use case → `ProfileError.Delete` | From server `HttpsError` | UI meaning |
|---|---|---|
| `PhraseMismatch` | `failed-precondition` | typed phrase wrong (also client-gated) |
| `OwnerReassignFailed` | `aborted` | couldn't reassign an owned crew — **retryable**, account preserved |
| `Unavailable` | `unauthenticated` / `internal` / else | cascade failed — **retryable**, session still valid |
| `NotImplemented` | (dead — stub gone) | keep the StringKey one release; never returned now |

### `ProfileViewModel.doDeleteAccount()` — the `Ok` branch teardown (spec §7, §13)

On `DeleteMyAccountUseCase` returning `Ok`, run **in this exact order**:

1. `analytics.track(AnalyticsEvent.AccountDeleted)` — fired in the VM AFTER the use case returns
   `Ok`, BEFORE `setUserId(null)` (so it still attributes to the about-to-be-cleared user). This is
   the only place the event fires. Never inside the use case.
2. `analytics.setUserId(null)`
3. `analytics.resetData()`
4. `signOut.signOut()` (`SignOutPort` — `ProfileViewModel` already depends on it for the sign-out
   button). This is the **local** teardown; the Auth user is already deleted server-side.
5. Do NOT add a navigation effect. The root-nav stage machine observes `SessionProvider.current`
   go to signed-out and navigates to `Route.SignIn` on its own.

On `Err`: set `deleteError` and fire **nothing** — no sign-out, no analytics reset (the user keeps
their session to retry).

### Koin + DI wiring (spec §7, §13)

- Add `analytics: AnalyticsPort = NoopAnalyticsTracker` as a `ProfileViewModel` ctor param (default
  keeps existing tests green).
- Switch the `authModule` binding from `viewModelOf(::ProfileViewModel)` to an **explicit**
  `viewModel { ProfileViewModel(…, analytics = get()) }` — `viewModelOf` would let the `Noop`
  default short-circuit graph resolution. `AnalyticsPort::class` is already in
  `AuthModuleVerifyTest.extraTypes`.

### Tests (spec §14.2)

- `ProfileErrorToStringKeyTest` — already updated by the domain task (`OwnerReassignFailed →
  DeleteAccountErrorOwnership`); no change needed.
- Extend `DeleteMyAccountUseCaseTest` and `ProfileViewModelTest` (commonTest, `UnconfinedTestDispatcher`
  + Turbine): assert on `Ok` that `RecordingAnalyticsTracker` recorded `AccountDeleted` then
  `setUserId(null)` + `resetData()`, and `SignOutPort.signOut()` was invoked; on `Err` assert
  `deleteError` set and NO sign-out / analytics reset.

### i18n (spec §9)

- Repurpose `auth_delete_account_error_ownership` copy in en/es (the key
  `AuthStringKey.DeleteAccountErrorOwnership` already maps to `Delete.OwnerReassignFailed`):
  en `"Couldn't reassign your crew's owner. Please try again."`,
  es `"No se pudo reasignar la propiedad de tu grupo. Inténtalo de nuevo."`
- `auth_delete_account_error_not_implemented` is now dead — keep the key + strings one release.

## Deploy steps the USER must run (carry to release; same as the data report)

1. `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec` (then `firestore:indexes`
   if a `FAILED_PRECONDITION: index required` surfaces for `authorId` collection-group queries on
   `meals`/`comments`, then `firestore:rules` no-op) — order per `docs/cicd-runbook.md` §6.
2. Confirm the Functions runtime SA has `firebaseauth.users.delete`.
3. App Store Connect + Google Play account-deletion declarations + public deletion-request URL.
4. On-device smoke: sign in → publish meal → delete account → SignIn lands, re-sign-in makes a fresh
   `accounts/{uid}`.
