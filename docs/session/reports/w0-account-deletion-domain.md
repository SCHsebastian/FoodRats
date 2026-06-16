# Report — `w0-account-deletion-domain`

**Date:** 2026-06-14
**Scope:** Account-deletion DOMAIN LAYER ONLY. Data/Cloud-Function and presentation are separate
later tasks (`w0-account-deletion-data`, `w0-account-deletion-presentation`) and were NOT touched
beyond the load-bearing exhaustiveness lock (see Decisions).

Spec: `docs/specs/2026-06-14-account-deletion-design.md` §3, §4.1, §10, §13.

## Prior work check

No prior account-deletion domain types existed on disk beyond the originals. The `AccountDeletionPort`
+ `AccountDeletionError` tree, `ProfileError`, the mapper, and `StubAccountDeletionPort` were the
pre-existing (stub-era) shape. Nothing partial to finish — implemented from the existing baseline.

## What changed

### `:core:domain`

- **`core/domain/.../account/AccountDeletionPort.kt`** — re-grouped `AccountDeletionError` per spec §10:
  - **Removed** `Ownership.OwnerOfActiveCrew` (and the whole `Ownership` group) — automatic
    server-side owner reassignment makes the "go transfer your crew first" precondition obsolete.
  - **Added** `Deletion.OwnerReassignFailed` (new group `Deletion`) — maps the server `aborted`
    HttpsError; retryable, account NOT partially deleted.
  - `Validation.PhraseMismatch` and `Backend.{NotImplemented, Unavailable}` unchanged.
    `NotImplemented` keeps a KDoc note that it is dead-but-kept one release (stub era).
  - **Rewrote the port KDoc** to the new contract: `requestDeletion` invokes the `deleteAccount`
    cascade **synchronously** and returns `Ok` only when the function reports completion. There is no
    "pending deletion" marker. `confirmation` is forwarded for server-side re-validation; a client may
    only delete itself (the adapter derives the caller's uid server-side). No port-surface change.
- **`core/domain/.../analytics/AnalyticsEvent.kt`** — added `data object AccountDeleted`
  (`name = "account_deleted"`, empty params, no PII), in a new `account` section before `consent`.
- **`core/domain/src/commonTest/.../analytics/AnalyticsTaxonomyTest.kt`** — added
  `AnalyticsEvent.AccountDeleted` to `allEvents` so the new leaf gets GA4-legality / no-PII coverage
  (the list is explicit, not reflective — a forgotten leaf silently loses coverage).

### `:feature:auth` (domain/error)

- **`feature/auth/.../domain/error/ProfileError.kt`** —
  - `ProfileError.Delete`: replaced `OwnerOfActiveCrew` with `OwnerReassignFailed`
    (`PhraseMismatch`, `NotImplemented`, `Unavailable` unchanged).
  - `AccountDeletionError.toProfileError()`: dropped the `Ownership.OwnerOfActiveCrew` arm, added
    `Deletion.OwnerReassignFailed -> ProfileError.Delete.OwnerReassignFailed`.

### `:feature:auth` (presentation mapper + test — touched only because they're the compile-time lock)

- **`feature/auth/.../presentation/profile/ProfileErrorToStringKey.kt`** —
  `Delete.OwnerReassignFailed -> AuthStringKey.DeleteAccountErrorOwnership` (repurposed key, §10).
- **`feature/auth/src/commonTest/.../profile/ProfileErrorToStringKeyTest.kt`** — renamed the test to
  `delete_owner_reassign_failed_maps_to_ownership_key`, asserting `OwnerReassignFailed → DeleteAccountErrorOwnership`.

## Decisions

- **Touched the presentation `ProfileErrorToStringKey` mapper + its test even though they live in
  `presentation/`.** The exhaustive `when` over `ProfileError` referenced the removed
  `Delete.OwnerOfActiveCrew` leaf, so the module would not compile (and the verify command
  `:feature:auth:testAndroidHostTest` would fail) without updating them. These are the
  error→StringKey *contract* lock, not UI; the presentation task still owns the `ProfileViewModel`
  delete-flow wiring (analytics event + setUserId(null)/resetData + signOut + explicit Koin binding).
  Updating the mapper here is the minimal change to keep the build green; nothing UI-facing was added.
- **Left `StubAccountDeletionPort.kt` in place.** It only references `Backend.NotImplemented` (still
  present), so it compiles. Deleting it + the `FirebaseAccountDeletionPort` swap is the data task (§4.2/§4.3).
- **`AuthStringKey.DeleteAccountErrorOwnership` exists and stays** (repurposed copy is the i18n task,
  spec §9 — not part of the domain task).
- **Owned-crew fate is fully decided by the spec** (§6: sole member → delete crew; owner + others →
  reassign to earliest-`joinedAt`, tie-broken by `accountId` ascending). That logic is TypeScript in
  the Cloud Function — the domain contract only needs `Deletion.OwnerReassignFailed` for the abort
  path, which is present. No domain-blocking product ambiguity.

## Verification

```
> Task :feature:auth:testAndroidHostTest
> Task :core:domain:testAndroidHostTest

BUILD SUCCESSFUL in 18s
102 actionable tasks: 18 executed, 84 up-to-date
```

`:core:domain:testAndroidHostTest` (incl. `AnalyticsTaxonomyTest` + Konsist no-Firebase/Android/Compose)
and `:feature:auth:testAndroidHostTest` (incl. `ProfileErrorToStringKeyTest` exhaustiveness) both green.

## What the next tasks now need

### `w0-account-deletion-data`
- Add `feature/auth/.../data/firebase/FirebaseAccountDeletionPort.kt` (GitLive Functions callable,
  region `europe-west3`, single `withContext(dispatchers.io)`; mirror `FirebaseImageUrlResolver`).
  Map HttpsError codes: `failed-precondition → Validation.PhraseMismatch`;
  `aborted → Deletion.OwnerReassignFailed`; `unauthenticated`/`internal`/else → `Backend.Unavailable`.
- Delete `StubAccountDeletionPort.kt`; swap the Koin binding in `AuthModule.kt`
  `single<AccountDeletionPort> { FirebaseAccountDeletionPort(dispatchers = get()) }`.
- Write the `functions/src/callables/deleteAccount.ts` cascade + vitest (spec §11/§14.1).

### `w0-account-deletion-presentation`
- `ProfileViewModel.doDeleteAccount()` `Ok` branch: `analytics.track(AnalyticsEvent.AccountDeleted)`
  → `analytics.setUserId(null)` → `analytics.resetData()` → `signOut.signOut()` (root nav handles
  navigation off `SessionProvider.current`). Add `analytics: AnalyticsPort = NoopAnalyticsTracker`
  ctor param; switch Koin to explicit `viewModel { ProfileViewModel(…, analytics = get()) }`.
- Extend `DeleteMyAccountUseCaseTest` + `ProfileViewModelTest` (spec §14.2).
- i18n: repurpose `auth_delete_account_error_ownership` copy in en/es (spec §9). The key
  `AuthStringKey.DeleteAccountErrorOwnership` already maps to `Delete.OwnerReassignFailed`.

### MANUAL (not codeable) — carry to deploy
- Deploy functions → any new `authorId` collection-group index → rules-noop (cicd-runbook §6).
- App Store Connect + Google Play "account deletion" declarations.
- Confirm Functions SA has `firebaseauth.users.delete`.

## Blockers

None.
