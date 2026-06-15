# Report — `w0-account-deletion-data`

**Date:** 2026-06-14
**Scope:** Account deletion DATA / INFRA layer only — the Firebase/GitLive adapter implementing
`AccountDeletionPort`, the Koin binding swap, and the `deleteAccount` Cloud Function cascade + its
vitest coverage. The Settings UI, confirmation dialog, sign-out/analytics-reset call sites, and
`ProfileViewModel` delete-flow wiring are explicitly OUT of scope (left for
`w0-account-deletion-presentation`).

Spec: `docs/specs/2026-06-14-account-deletion-design.md` §4.2, §4.3, §5, §6, §11, §14.1.
Domain contract: `docs/session/handoffs/w0-account-deletion-domain.md`.

## Prior work check

No partial data-task work existed on disk. The only account-deletion artifacts were the domain
task's outputs: the `AccountDeletionPort` + `AccountDeletionError` tree (`:core:domain`), the
`ProfileError.Delete` tree + mapper (`:feature:auth`), and `StubAccountDeletionPort`
(`:feature:auth/data/firebase/`). `functions/src/callables/` held only `mintPlateUrls.ts`. Built
the real adapter + function from the existing baseline.

## What changed

### `:feature:auth` (data adapter + Koin + build)

- **NEW `feature/auth/.../data/firebase/FirebaseAccountDeletionPort.kt`** — the GitLive Functions
  callable adapter, copied verbatim in shape from `core/data/.../image/FirebaseImageUrlResolver.kt`:
  `Firebase.functions("europe-west3").httpsCallable("deleteAccount").invoke(req).data<Resp>()`,
  a single `withContext(dispatchers.io)` boundary, `runCatching { … }.fold(…)` with vendor-error
  mapping by string-matching `Throwable.message` for the `HttpsError` code. The request carries
  **no** `accountId` (only the `confirmation` phrase) — the function derives the caller uid from
  `request.auth.uid`. Private `@Serializable DeleteAccountRequest(confirmation)` /
  `DeleteAccountResponse(deleted)` DTOs live in-file (vendor-confined). Error mapping:
  | `HttpsError` code | → `AccountDeletionError` |
  |---|---|
  | `failed-precondition` | `Validation.PhraseMismatch` |
  | `aborted` | `Deletion.OwnerReassignFailed` |
  | `unauthenticated` / `internal` / anything else | `Backend.Unavailable` |
- **DELETED `feature/auth/.../data/firebase/StubAccountDeletionPort.kt`.**
- **`feature/auth/.../di/AuthModule.kt`** — swapped the binding:
  `single<AccountDeletionPort> { FirebaseAccountDeletionPort(dispatchers = get()) }` (was
  `StubAccountDeletionPort()`); updated the import. `DeleteMyAccountUseCase` / `ProfileViewModel`
  bindings untouched (the presentation task changes the `ProfileViewModel` binding to explicit).
- **`feature/auth/build.gradle.kts`** — added `implementation(libs.firebase.functions)` to
  `commonMain.dependencies`. The `firebase-gitlive` bundle does **not** include
  `dev.gitlive:firebase-functions` (it's common/auth/firestore/storage only); `:core:data` already
  pulls it in standalone for `FirebaseImageUrlResolver`, so `:feature:auth` now does the same.

### `functions/` (Cloud Function cascade + test)

- **NEW `functions/src/callables/deleteAccount.ts`** — mirrors `mintPlateUrls.ts`: a thin `onCall`
  ({ region: "europe-west3" }) wrapper over a dependency-injected, Admin-SDK-free testable core
  (`deleteAccountCore(deps, uid, req)`). The cascade order matches spec §5 exactly:
  unauth guard → server-side phrase re-validation (`failed-precondition` on mismatch) → #1+2
  authored meals (`recursiveDelete` + plate `deleteBlob`) → #3 comments on others' meals → #4
  votes on others' meals (`removeRating` txn) → #5+9 crew memberships (reassign-or-delete) →
  #6+7+8 `accounts/{uid}` + `avatars/{uid}.jpg` + top-level `devices/{uid}` → **#10
  `deleteAuthUser` LAST**. On a crew reassign throw it raises `HttpsError("aborted")` **before**
  `deleteAuthUser` (account preserved, retryable). The `onCall` wires the real Admin-SDK deps:
  - `authoredMeals` / `authoredComments` — `collectionGroup("meals"|"comments").where("authorId","==",uid)`.
  - `votedMeals` — iterates the crews `uid` belongs to and collects meals NOT authored by `uid`
    carrying `ratings[uid]` (map-key existence isn't directly queryable; crews are ≤ 8 members so
    the bounded per-crew scan is fine — spec §17 risk).
  - `removeRating` — a Firestore transaction: `FieldValue.delete()` the `ratings.{uid}` key and
    `FieldValue.increment(-score)` / `increment(-1)` the `ratingSum`/`voterCount` aggregates;
    idempotent (no-op if the key is already gone).
  - `reassignOrDeleteCrew` — applies the extracted pure `planCrewReassignment(crew, uid)` (§6):
    non-owner → drop (`arrayRemove` + `members.{uid}` delete); sole-member owner → hard-delete the
    crew + its `crewCodes/{code}` (mirrors `CrewRepository.leave`); owner+others → reassign to the
    earliest-`joinedAt` remaining member (ties broken by `accountId` ascending) in one update.
  - `deleteBlob` — `getStorage().bucket().file(p).delete({ ignoreNotFound: true })` (idempotent).
  - `recursiveDelete` — `db.recursiveDelete(db.doc(p))` (safe on missing paths).
  - `deleteAuthUser` — `getAuth().deleteUser(uid)`.

  **Exported `planCrewReassignment` as a pure helper** (returns `{kind:"drop"|"delete"|"reassign", newOwnerId?}`)
  so §6 is unit-tested directly without the Admin SDK, per spec §14.1.
- **`functions/src/index.ts`** — `export { deleteAccount } from "./callables/deleteAccount";`.
- **NEW `functions/__tests__/deleteAccount.test.ts`** — 11 vitest cases driving `deleteAccountCore`
  with recording fakes (call-order tracked) + `planCrewReassignment` directly. Covers all §14.1
  cases: unauth (no deps called), phrase mismatch (nothing destroyed), phrase whitespace-trim,
  happy-path full cascade (every meal/plate/comment/rating/crew/identity deleted), `deleteAuthUser`
  exactly-once-and-last (asserts it's the final call), the four `planCrewReassignment` policy arms
  (drop / sole-delete / earliest-joinedAt reassign / accountId tie-break), reassign-failure →
  `aborted` with `deleteAuthUser` never reached, and idempotent re-run over a half-deleted fixture.

## Decisions

- **`votedMeals` iterates the caller's crews rather than a `collectionGroup` on `ratings[uid]`.**
  Map-key existence is not Firestore-queryable; the spec (§5 #4, §17) prescribes the per-crew scan,
  bounded because `uid`'s crew set is already loaded for #5. Tolerant of timeout via re-run.
- **Extracted `planCrewReassignment` as an exported pure function** (the spec sketch inlined the
  policy in `reassignOrDeleteCrew`). §14.1 explicitly asks to "extract it as an exported pure
  helper taking a `CrewSnap`" so the policy is testable without Admin-SDK fakes — done.
- **`platePath` fallback** added to the `onCall` deps (`crews/{crewId}/meals/{mealId}.jpg` when the
  doc has no persisted `platePath`), mirroring `onMealDeleted`'s fallback — keeps plate reclamation
  robust for older meal docs.
- **No `firestore.rules` edit** (spec §12: the Admin SDK bypasses rules; client rules already deny
  cross-member deletes). **No `firestore.indexes.json` edit pre-emptively** — the `authorId`
  collection-group single-field indexes may need enabling at first invocation; flagged as MANUAL.
- **Added `libs.firebase.functions` to `:feature:auth`** — the only build-config change needed; the
  GitLive Functions binding is not in the shared `firebase-gitlive` bundle.

## Verification

### `pnpm --dir functions test` (last lines)
```
 Test Files  5 passed (5)
      Tests  39 passed (39)
   Duration  606ms (...)
```
(11 of the 39 are the new `deleteAccount.test.ts`; the lone stderr line is the expected
`logger.error` emitted by the reassign-failure case, which still passes.)

Also `pnpm --dir functions build` (tsc) — `EXIT=0`, clean (production code with the real
Admin-SDK deps type-checks).

### `./gradlew :feature:auth:testAndroidHostTest` (last lines)
```
> Task :feature:auth:testAndroidHostTest

BUILD SUCCESSFUL in 5s
90 actionable tasks: 11 executed, 79 up-to-date
```
Includes `AuthModuleVerifyTest` (Koin graph check) — the new `FirebaseAccountDeletionPort` binding
resolves cleanly (it needs only `DispatcherProvider`, already in `extraTypes`). The two warnings in
the build log are pre-existing `expect/actual` Beta notices on `GoogleAuthClient`, unrelated.

## MANUAL — deploy/store steps (not codeable here; carry to release)

1. Deploy functions: `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec`
   (then `firestore:indexes` if needed, then `firestore:rules` is a no-op) — order per
   `docs/cicd-runbook.md` §6.
2. If the first invocation reports `FAILED_PRECONDITION: index required`, add the `authorId`
   single-field collection-group index for `meals` and `comments` to `firestore.indexes.json` and
   re-deploy `firestore:indexes`.
3. Confirm the Functions runtime service account has `firebaseauth.users.delete` (default for the
   default Functions SA; note if a custom SA is in use).
4. App Store Connect (App Privacy → Account Deletion) + Google Play Console (Data safety → "Provide
   a way for users to request that their data is deleted") declarations, plus the public
   deletion-request URL Play wants.
5. On-device smoke after deploy: sign in → publish a meal → delete account → confirm SignIn lands
   and re-signing in creates a fresh `accounts/{uid}` (old data gone).

## Blockers

None.
