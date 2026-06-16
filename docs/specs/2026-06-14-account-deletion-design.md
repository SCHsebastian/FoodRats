# Account deletion — permanent, server-cascaded

**Date:** 2026-06-14
**Status:** Design — pending implementation

## 1. Decision

Ship permanent, irreversible account deletion. A user opens Settings → Delete account, types the confirmation phrase, confirms a destructive dialog, and their account plus every trace of it is erased: the `accounts/{uid}` doc, FCM device tokens, avatar blob, every meal they authored across every crew (with the meal's plate, comments, and deprecated ratings), their comments and ratings on *other* users' meals, their crew memberships, and the Firebase Auth user record.

The client-side flow already exists end-to-end against a stub (`StubAccountDeletionPort` returns `AccountDeletionError.Backend.NotImplemented`). This spec defines the missing backend — a client-callable Cloud Function `deleteAccount` (region `europe-west3`) that performs the cascade with the Admin SDK — and the thin client adapter that replaces the stub, plus the post-deletion analytics + sign-out finish.

The cascade runs **server-side** because the client lacks permission to delete other members' documents (its own comments/ratings live under meals it doesn't own) or to delete the Auth user record. Firestore deletes do not cascade and Storage is never touched by a Firestore delete, so the function reuses the `onMealDeleted` reclamation pattern (`recursiveDelete` + blob delete) per meal.

## 2. Motivation

Account deletion is **store-blocking**, not optional:

- **Apple App Store Guideline 5.1.1(v)** — any app that supports account creation must let the user initiate account deletion from within the app. FoodRats creates an `accounts/{uid}` doc on first sign-in, so it qualifies. Apps without an in-app delete path are rejected at review.
- **GDPR Art. 17 (right to erasure)** — an EU user can demand their personal data be deleted. Self-service deletion is the cleanest way to honour this for a closed-group beta with no support desk.

The existing `StubAccountDeletionPort` was built so the client gates (phrase confirmation, destructive dialog, sign-out cleanup) ship and are exercised; it surfaces "contact support" via `AccountDeletionError.Backend.NotImplemented`. That stub is the only thing standing between the current build and a submittable one.

## 3. Scope

In scope: the `deleteAccount` Cloud Function (cascade + Auth-user deletion) and its vitest coverage; a `FirebaseAccountDeletionPort` (GitLive Functions callable) replacing `StubAccountDeletionPort` and its Koin binding; the post-deletion finish (`AnalyticsPort.setUserId(null)` + `resetData()`, then `SignOutPort.signOut()`, then navigate to SignIn); a new `AnalyticsEvent.AccountDeleted` leaf; refinement of the `AccountDeletionError` / `ProfileError.Delete` trees (drop the now-dead `Ownership.OwnerOfActiveCrew`, add `Deletion.OwnerReassignFailed`); the owned-crew fate logic; and the affected tests.

Out of scope: the entire client UI (`DeleteAccountScreen`, `ProfileViewModel` delete state, the phrase gate, `DeleteMyAccountUseCase`, `AuthStringKey.DeleteAccount*` strings, `ProfileErrorToStringKey`) — already built and shipping. A soft "deactivate / 30-day grace" window (we hard-delete). Export-my-data (GDPR Art. 20 portability) — a separate spec. Deleting the user's *content* in crews they want to keep contributing to under a different account (deletion is all-or-nothing per the cascade in §5). A migration of historical orphans created before this function existed.

## 4. Architecture

### 4.1 The port is already declared — `:core:domain/account/AccountDeletionPort.kt`

```kotlin
interface AccountDeletionPort {
    suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError>
}
```

No port surface change. The semantics tighten: `requestDeletion` no longer "writes a pending-deletion marker" (the KDoc's original plan) — it invokes the `deleteAccount` callable **synchronously** and returns `Ok` only when the function reports the cascade complete. Update the KDoc to match. The confirmation phrase is forwarded so the function re-validates server-side (defense in depth — see §10).

### 4.2 New adapter — `:feature:auth/data/firebase/FirebaseAccountDeletionPort.kt`

Replaces `StubAccountDeletionPort`. Copies the GitLive-Functions callable shape from `core/data/.../image/FirebaseImageUrlResolver.kt` exactly (same `Firebase.functions(region).httpsCallable(NAME).invoke(req).data<Resp>()` call, same region default `"europe-west3"`, same single `withContext(dispatchers.io)` boundary, same `runCatching { … }.fold(…)` error mapping by inspecting `Throwable.message` for the `HttpsError` code).

```kotlin
class FirebaseAccountDeletionPort(
    private val dispatchers: DispatcherProvider,
    private val region: String = "europe-west3",
) : AccountDeletionPort {

    private val functions by lazy { Firebase.functions(region) }

    override suspend fun requestDeletion(
        accountId: AccountId,
        confirmation: String,
    ): Result<Unit, AccountDeletionError> = withContext(dispatchers.io) {
        runCatching {
            functions.httpsCallable(CALLABLE)
                .invoke(DeleteAccountRequest(confirmation = confirmation))
                .data<DeleteAccountResponse>()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { t -> Result.failure(t.toAccountDeletionError()) },
        )
    }

    // failed-precondition → PhraseMismatch; aborted → Deletion.OwnerReassignFailed;
    // everything else → Backend.Unavailable. Mirror FirebaseImageUrlResolver.toImageUrlError().
    private fun Throwable.toAccountDeletionError(): AccountDeletionError { /* string-match HttpsError code */ }

    private companion object { const val CALLABLE = "deleteAccount" }
}

@Serializable private data class DeleteAccountRequest(val confirmation: String)
@Serializable private data class DeleteAccountResponse(val deleted: Boolean = true)
```

The request carries **no** `accountId` — the function derives the caller's uid from `request.auth.uid`. A client may only delete *itself*; passing an id would invite abuse and the function would ignore it anyway.

### 4.3 Koin binding swap — `:feature:auth/di/AuthModule.kt`

```kotlin
// before:
single<AccountDeletionPort> { StubAccountDeletionPort() }
// after:
single<AccountDeletionPort> { FirebaseAccountDeletionPort(dispatchers = get()) }
```

`DeleteMyAccountUseCase` and `ProfileViewModel` bindings are unchanged. `StubAccountDeletionPort.kt` is deleted.

## 5. Data / cascade — the full deletion graph

The function deletes, for caller uid `U`, **exhaustively**:

| # | Path / collection | What | How |
|---|---|---|---|
| 1 | `crews/{crewId}/meals/{mealId}` where `authorId == U`, for every crew where `U ∈ memberIds` | Every meal `U` authored | `collectionGroup("meals").where("authorId", "==", U)` → per doc: `recursiveDelete(doc)` (sweeps the doc + its `comments` + deprecated `ratings` subcollections) |
| 2 | `crews/{crewId}/meals/{mealId}.jpg` (or the doc's persisted `platePath`) for each meal from #1 | The plate blob per authored meal | `bucket().file(platePath).delete({ ignoreNotFound: true })` — same as `onMealDeleted` step 2 |
| 3 | `crews/{crewId}/meals/{otherMealId}/comments/*` authored by `U` on **other** users' meals | `U`'s comments on others' meals | `collectionGroup("comments").where("authorId", "==", U)` → delete each |
| 4 | `crews/{crewId}/meals/{otherMealId}` — the `ratings.{U}` map key + recomputed `ratingSum`/`voterCount` on others' meals | `U`'s ratings/votes on others' meals | `collectionGroup("meals").where("ratings.{U}.score" exists)` is not queryable; instead iterate the crews `U` belongs to and, per meal not authored by `U` that has `ratings[U]`, `FieldValue.delete()` the key and decrement the aggregates in a transaction. (Best-effort — see Risks.) Also delete any straggler doc under deprecated `…/ratings/{U}` via `collectionGroup("ratings")` where the doc id is `U`. |
| 5 | `crews/{crewId}` membership where `U ∈ memberIds` | `U`'s crew memberships | per crew: the **owned-crew handling** in §6 (delete-or-reassign), then for non-owned crews remove `U` from `memberIds` + the `members` map in a transaction |
| 6 | `accounts/{uid}` (== `accounts/{U}`) + its `private/*` and `devices/*` subcollections | The identity doc, PII, FCM tokens | `recursiveDelete(doc("accounts/U"))` |
| 7 | `avatars/{U}.jpg` | Avatar blob | `bucket().file("avatars/U.jpg").delete({ ignoreNotFound: true })` |
| 8 | `devices/{U}` (top-level, if present) | Top-level device-token tree (legacy/parallel to `accounts/{U}/devices`) | `recursiveDelete(doc("devices/U"))` — tolerate not-found |
| 9 | `crewCodes/{CODE}` for any crew deleted in #5 | Orphaned invite codes | delete alongside the crew (mirror `CrewRepository.leave`'s last-member path) |
| 10 | **Firebase Auth** user record `U` | The auth identity | `getAuth().deleteUser(U)` — **last**, after all Firestore/Storage succeed (see Risks §16 on ordering) |

Ordering inside the function: do #1–#9 (data) first; do #10 (`deleteUser`) **last**. If any data step throws, abort *before* `deleteUser` so the user can retry from a still-valid session — an Auth-deleted user with residual Firestore data is the worst outcome (orphaned, un-retryable). Each individual delete is wrapped to tolerate not-found / double-fire so a re-run after a partial failure converges (idempotent — see §16).

The `collectionGroup` queries (#1, #3) need Firestore composite/single-field indexes on `authorId`. These exist already for the app's own reads; if a query fails with `FAILED_PRECONDITION: index required`, add the single-field `authorId` index to `firestore.indexes.json` (note as a MANUAL step under §17 if the deploy surfaces it).

## 6. Owned-crew handling (the key decision)

When `U` is the **owner** of a crew (`crew.ownerId == U`), deleting `U` must not strand the crew's other members under a dangling owner. Default policy:

- **Sole member** (`memberIds == [U]`): delete the crew entirely — its meals (via #1/#2, which already cover them since `U` authored them in a solo crew), its `crewCodes/{CODE}` invite, and the crew doc. Identical to `CrewRepository.leave`'s last-member hard-delete path.
- **Owner with other members**: **reassign ownership** to the earliest-`joinedAt` remaining member (the `members` map / `Member(accountId, joinedAt)` list, minimum `joinedAt`, ties broken by `accountId` ascending for determinism). In one transaction: set `crew.ownerId = newOwner`, remove `U` from `memberIds` + the `members` map. `U`'s authored meals in that crew are still deleted by #1; the crew and its other members' meals survive under the new owner.

This is the **default and the load-bearing decision** of this spec. Rationale: silently deleting a multi-member crew because its founder left would destroy other people's content — unacceptable. Reassigning to the longest-tenured member is the least-surprising automatic choice and needs no user prompt mid-deletion (the deletion is already irreversible; blocking it on "pick a new owner" would be hostile UX and re-introduce the `OwnerOfActiveCrew` dead-end the stub modelled).

If reassignment fails (e.g. the transaction can't resolve a next owner because the `members` map is malformed), the function throws `HttpsError("aborted", …)`, which the client maps to `AccountDeletionError.Deletion.OwnerReassignFailed` → a retryable error banner. The account is **not** partially deleted in that case (owned-crew handling runs inside #5, before `deleteUser`).

The old `AccountDeletionError.Ownership.OwnerOfActiveCrew` leaf (which modelled "you can't delete because you own a crew — go transfer it first") is **removed** — automatic reassignment makes the precondition obsolete.

## 7. Client post-deletion flow

On `DeleteMyAccountUseCase` returning `Ok` (i.e. the function reported the cascade done), `ProfileViewModel.doDeleteAccount()` must finish the local teardown. Today its `Ok` branch only clears UI state. Extend it to:

1. `analytics.track(AnalyticsEvent.AccountDeleted)` — fired in the VM **after** the use case returns `Ok`, never inside the use case (analytics rule). This is the only place the event fires.
2. `analytics.setUserId(null)` then `analytics.resetData()` — sever the analytics identity and clear locally cached analytics state (no events should attribute to a deleted user).
3. `signOut.signOut()` — `SignOutPort` clears platform auth state, the local session token, and session-derived caches (e.g. active crew id) so the next sign-in starts clean. (The Auth user is already deleted server-side; `signOut` here is the *local* clean-up — Firebase's local sign-out succeeds regardless of whether the remote user still exists.)
4. The existing root-nav stage machine, observing `SessionProvider.current` go to "signed out", navigates to `Route.SignIn` on its own. No explicit navigation effect is added in `ProfileViewModel`.

`ProfileViewModel` gains an `analytics: AnalyticsPort = NoopAnalyticsTracker` constructor param (default keeps existing tests green) and a `signOut` call in the delete `Ok` branch (it already depends on `SignOutPort` for the sign-out button). The Koin `viewModelOf(::ProfileViewModel)` binding stays `viewModelOf` only if every dependency resolves; because `AnalyticsPort` now has a default, switch the binding to an **explicit** `viewModel { ProfileViewModel(…, analytics = get()) }` so the real tracker is injected (per the analytics-base convention — `viewModelOf` would let the `Noop` default short-circuit graph resolution).

## 8. Presentation (already built — referenced for completeness)

- **Settings entry.** `AuthStringKey.ProfileDeleteAccountRow` / `ProfileDeleteAccountSubtitle` render a Danger-Zone row on the Profile surface that opens `DeleteAccountScreen` (`ProfileIntent.OpenDeleteAccount` → `deleteScreenOpen = true`).
- **`DeleteAccountScreen`** (`:feature:auth/presentation/profile/DeleteAccountScreen.kt`) shows a warning header, a consequences checklist (`DeleteAccountWarningMeals` / `…Ratings` / `…Crews` / `…Irreversible`), a phrase gate (`FrTextField`, label = the expected phrase), and a `FrButton(variant = Danger)` enabled only when `deleteConfirmation == expectedPhrase`. Confirming opens `FrConfirmDialog(destructive = true)` (`DeleteAccountDialog{Title,Body,Confirm,Cancel}`). The danger color is `LocalFrSemanticColors.current.danger`.
- **Expected phrase.** `ProfileViewModel.expectedDeletePhrase()` builds `"DELETE <displayName>"` (verb `DELETE_VERB = "DELETE"`, source-of-truth comparison in English); the Spanish UI shows the localized template `DeleteAccountPhraseTemplate` but the typed-string comparison stays English.

No presentation change is required by this spec **except** the `ProfileViewModel.doDeleteAccount()` `Ok`-branch additions in §7.

## 9. i18n

The strings exist (`AuthStringKey.DeleteAccount*` + `auth_delete_account_*` in en/es). Two adjustments:

| Key | en | es | Notes |
|---|---|---|---|
| `DeleteAccountErrorOwnership` (repurpose, do **not** delete) | `Couldn't reassign your crew's owner. Please try again.` | `No se pudo reasignar la propiedad de tu grupo. Inténtalo de nuevo.` | Was "you own a crew, transfer it first". Now surfaces `Deletion.OwnerReassignFailed` — a *transient* reassignment failure, retryable |
| `DeleteAccountErrorBackend` (existing) | `Something went wrong deleting your account. Please try again.` | `Algo salió mal al eliminar tu cuenta. Inténtalo de nuevo.` | Unchanged copy; now also the target for `Backend.Unavailable` |
| `DeleteAccountErrorNotImplemented` (existing) | — | — | Becomes **dead** once the stub is removed. Keep the key + strings for one release (a stale build pointing at the new function still resolves it), delete in a follow-up |

`AnalyticsEvent.AccountDeleted` is a telemetry event, not user-visible — no string key.

## 10. Error model + mapper + test

The `AccountDeletionError` tree (`:core:domain/account/AccountDeletionPort.kt`) is **re-grouped** (sealed interface + `data object` leaves, never enum, no `Unknown`):

```kotlin
sealed interface AccountDeletionError {
    sealed interface Validation : AccountDeletionError {
        data object PhraseMismatch : Validation
    }
    sealed interface Backend : AccountDeletionError {
        data object Unavailable : Backend
        // NotImplemented kept one release for the stub-era build; remove in follow-up.
        data object NotImplemented : Backend
    }
    sealed interface Deletion : AccountDeletionError {
        data object OwnerReassignFailed : Deletion   // new — HttpsError("aborted")
    }
    // Ownership.OwnerOfActiveCrew REMOVED — auto-reassignment makes it obsolete.
}
```

`ProfileError.Delete` (`:feature:auth/domain/error/ProfileError.kt`) mirrors:

```kotlin
sealed interface Delete : ProfileError {
    data object PhraseMismatch : Delete
    data object NotImplemented : Delete          // dead-but-kept one release
    data object Unavailable : Delete
    data object OwnerReassignFailed : Delete     // replaces OwnerOfActiveCrew
}
```

`AccountDeletionError.toProfileError()` updates: drop the `Ownership.OwnerOfActiveCrew` arm, add `Deletion.OwnerReassignFailed -> ProfileError.Delete.OwnerReassignFailed`. `ProfileError.toStringKey()` (`ProfileErrorToStringKey.kt`) updates: `Delete.OwnerReassignFailed -> AuthStringKey.DeleteAccountErrorOwnership` (repurposed key), drop the `OwnerOfActiveCrew` arm.

`ProfileErrorToStringKeyTest` (`commonTest`) is the exhaustiveness lock: rename `delete_ownership_maps_to_ownership_key` → assert `ProfileError.Delete.OwnerReassignFailed.toStringKey() == AuthStringKey.DeleteAccountErrorOwnership`; keep the `NotImplemented` / `Unavailable` / `PhraseMismatch` assertions. The `when` over `ProfileError` stays exhaustive so a forgotten branch fails to compile.

## 11. Cloud Function — `functions/src/callables/deleteAccount.ts`

Mirror `mintPlateUrls.ts` (thin `onCall` wrapper over a dependency-injected, Admin-SDK-free testable core — the `weeklyDigest.paginateCrews` pattern) so the cascade is unit-testable without the emulator.

```typescript
import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions/v2";

export interface DeleteAccountRequest { confirmation: string; }
export interface DeleteAccountResponse { deleted: true; }

// Injected so the core is testable with fakes (no Admin SDK), like buildSignedUrls / paginateCrews.
export interface DeletionDeps {
  expectedPhrase: (uid: string) => Promise<string>;       // "DELETE <displayName>" from accounts/{uid}
  authoredMeals: (uid: string) => Promise<MealRef[]>;     // collectionGroup meals where authorId == uid
  authoredComments: (uid: string) => Promise<DocRef[]>;   // collectionGroup comments where authorId == uid
  votedMeals: (uid: string) => Promise<MealRef[]>;        // meals (not authored by uid) carrying ratings[uid]
  memberCrews: (uid: string) => Promise<CrewSnap[]>;      // crews where uid in memberIds
  recursiveDelete: (path: string) => Promise<void>;
  deleteBlob: (path: string) => Promise<void>;            // ignoreNotFound
  removeRating: (mealPath: string, uid: string) => Promise<void>;     // FieldValue.delete + recompute, txn
  reassignOrDeleteCrew: (crew: CrewSnap, uid: string) => Promise<void>; // §6 policy
  deleteAuthUser: (uid: string) => Promise<void>;
}

export interface MealRef { path: string; platePath: string; }
export interface DocRef { path: string; }
export interface CrewSnap { crewId: string; ownerId: string; memberIds: string[]; members: Record<string, { joinedAt: number }>; code: string | null; }

export async function deleteAccountCore(
  deps: DeletionDeps,
  uid: string | undefined,
  req: DeleteAccountRequest,
): Promise<DeleteAccountResponse> {
  if (!uid) throw new HttpsError("unauthenticated", "Sign-in required.");

  // Re-validate the phrase server-side (defense in depth — the client also gates it).
  const expected = await deps.expectedPhrase(uid);
  if ((req.confirmation ?? "").trim() !== expected) {
    throw new HttpsError("failed-precondition", "Confirmation phrase did not match.");
  }

  // 1+2: authored meals (sweeps comments/ratings) + their plates.
  for (const m of await deps.authoredMeals(uid)) {
    await deps.recursiveDelete(m.path);
    await deps.deleteBlob(m.platePath);
  }
  // 3: comments on OTHER users' meals.
  for (const c of await deps.authoredComments(uid)) await deps.recursiveDelete(c.path);
  // 4: ratings on OTHER users' meals (decrement aggregates in a txn).
  for (const m of await deps.votedMeals(uid)) await deps.removeRating(m.path, uid);
  // 5+9: crew memberships — reassign-or-delete per §6 (deletes crewCodes for deleted crews).
  for (const crew of await deps.memberCrews(uid)) {
    try {
      await deps.reassignOrDeleteCrew(crew, uid);
    } catch (e) {
      logger.error(`deleteAccount: crew ${crew.crewId} reassign/delete failed`, e);
      throw new HttpsError("aborted", "Could not reassign crew ownership.");
    }
  }
  // 6+7+8: identity doc (+private+devices), avatar, top-level devices.
  await deps.recursiveDelete(`accounts/${uid}`);
  await deps.deleteBlob(`avatars/${uid}.jpg`);
  await deps.recursiveDelete(`devices/${uid}`);

  // 10: Auth user LAST — only after all data is gone, so a mid-failure leaves a retryable session.
  await deps.deleteAuthUser(uid);

  return { deleted: true };
}

export const deleteAccount = onCall(
  { region: "europe-west3" },
  async (request: CallableRequest<DeleteAccountRequest>): Promise<DeleteAccountResponse> => {
    const db = getFirestore();
    const deps: DeletionDeps = {
      expectedPhrase: async (uid) => {
        const name = ((await db.doc(`accounts/${uid}`).get()).data()?.displayName as string | undefined)?.trim() ?? "";
        return name === "" ? "DELETE" : `DELETE ${name}`;
      },
      authoredMeals: async (uid) => (await db.collectionGroup("meals").where("authorId", "==", uid).get())
        .docs.map((d) => ({ path: d.ref.path, platePath: (d.data().platePath as string) ?? `${d.ref.parent.parent!.path}/meals/${d.id}.jpg` })),
      authoredComments: async (uid) => (await db.collectionGroup("comments").where("authorId", "==", uid).get())
        .docs.map((d) => ({ path: d.ref.path })),
      votedMeals: async (uid) => { /* iterate memberCrews(uid)'s meals where ratings[uid] exists, skip own-authored */ return []; },
      memberCrews: async (uid) => (await db.collection("crews").where("memberIds", "array-contains", uid).get())
        .docs.map((d) => ({ crewId: d.id, ownerId: d.data().ownerId, memberIds: d.data().memberIds ?? [], members: d.data().members ?? {}, code: d.data().code ?? null })),
      recursiveDelete: (p) => db.recursiveDelete(db.doc(p)),
      deleteBlob: async (p) => { await getStorage().bucket().file(p).delete({ ignoreNotFound: true }); },
      removeRating: async (mealPath, uid) => { /* txn: delete ratings[uid], recompute ratingSum/voterCount */ },
      reassignOrDeleteCrew: async (crew, uid) => { /* §6: sole→delete crew+code+meals; else txn reassign owner + drop uid */ },
      deleteAuthUser: (uid) => getAuth().deleteUser(uid).then(() => undefined),
    };
    try {
      return await deleteAccountCore(deps, request.auth?.uid, request.data);
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("deleteAccount failed", e);
      throw new HttpsError("internal", "Could not delete account.");
    }
  },
);
```

Export it from `functions/src/index.ts`: `export { deleteAccount } from "./callables/deleteAccount";`.

`HttpsError` code → client error: `unauthenticated`/`internal` → `Backend.Unavailable`; `failed-precondition` → `Validation.PhraseMismatch`; `aborted` → `Deletion.OwnerReassignFailed`.

## 12. Firestore security rules

The function runs with the Admin SDK and **bypasses** rules entirely, so no rule needs to *grant* the cascade. Keep the **client** rules denying direct deletes of others' data — the cascade must be server-only:

- `accounts/{uid}` already `allow write: if request.auth.uid == uid` — a client can already delete only its *own* account doc, but it cannot reach other members' comments/ratings or the Auth record, which is exactly why the function exists. **No change.**
- `crews/{crewId}` delete already allows owner-or-last-member; `crewCodes` already client-managed on crew create/delete. **No change** — the function's reassign/delete writes go through Admin and skip these.
- `crews/{crewId}/meals/{mealId}` delete already allows author-or-owner. **No change.**

One thing to confirm at deploy: the `collectionGroup("meals")` / `collectionGroup("comments")` queries are issued by the Admin SDK (rules-exempt) so they need no read rule, but they **do** need the `authorId` single-field index enabled for collection-group scope. If the deploy or first invocation reports `FAILED_PRECONDITION`, add it to `firestore.indexes.json` (MANUAL, §17).

No `firestore.rules` edit is required by this spec. (Belt-and-suspenders option, deferred: a `pendingDeletion` marker doc the client could write to trigger a *background* trigger instead of a callable — rejected here in favour of the synchronous callable so the UI can report real completion, matching the `mintPlateUrls` posture.)

## 13. Analytics

Add one leaf to the taxonomy (`:core:domain/analytics/AnalyticsEvent.kt`), following the existing `data object` events (`MealComposerOpened`, `StreakViewed`) — past-tense snake_case GA4 name, no PII:

```kotlin
data object AccountDeleted : AnalyticsEvent {
    override val name = "account_deleted"
    override val params = emptyMap<String, AnalyticsValue>()
}
```

Fired once, in `ProfileViewModel.doDeleteAccount()` **after** `DeleteMyAccountUseCase` returns `Ok` and **before** `setUserId(null)` (so the event still attributes to the about-to-be-cleared user). `AnalyticsTaxonomyTest` (the snake_case/name-uniqueness lock) picks it up automatically. The feature's existing `*ModuleVerifyTest` already lists `AnalyticsPort::class` in `extraTypes` (added with the analytics base) — confirm `ProfileViewModel`'s new explicit binding resolves it.

## 14. Tests

### 14.1 vitest — `functions/__tests__/deleteAccount.test.ts` (cascade completeness)

Drive `deleteAccountCore` with fake `DeletionDeps` (recording fakes, like `mintPlateUrls.test.ts` drives `buildSignedUrls`). Assert:

- **unauth** → throws `HttpsError("unauthenticated")`, no deps called.
- **phrase mismatch** → `expectedPhrase` returns `"DELETE Ana"`, `confirmation = "delete ana"` → throws `failed-precondition`; **no** delete fake invoked (nothing destroyed on a bad phrase).
- **happy path** → with 2 authored meals, 3 cross-crew comments, 1 voted meal, 2 crews (one sole-owned, one co-owned): every authored-meal `recursiveDelete` + `deleteBlob` fires, every comment deleted, `removeRating` fires for the voted meal, `reassignOrDeleteCrew` fires per crew, `accounts/{uid}` + `avatars/{uid}.jpg` + `devices/{uid}` deleted, and `deleteAuthUser` is called **exactly once, last** (assert call order — auth deletion strictly after the last data delete).
- **owned-crew reassign** → unit-test `reassignOrDeleteCrew`'s policy directly (extract it as an exported pure helper taking a `CrewSnap`): sole member → returns a "delete crew + code" plan; owner+others → "reassign to min-`joinedAt` member, drop uid"; tie on `joinedAt` → deterministic `accountId`-ascending pick.
- **reassign failure** → `reassignOrDeleteCrew` rejects → core throws `HttpsError("aborted")` and `deleteAuthUser` is **never** called (the account survives a reassignment failure).
- **idempotent re-run** → all blob/doc fakes tolerate not-found; a second run over an already-half-deleted fixture completes without throwing.

### 14.2 Kotlin — use case + ViewModel + mapper

- `ProfileErrorToStringKeyTest` (`commonTest`): update per §10 — `OwnerReassignFailed → DeleteAccountErrorOwnership`, drop `OwnerOfActiveCrew`; keep the exhaustive `when` lock.
- `DeleteMyAccountUseCaseTest` (new/extend, `commonTest`): with a fake `AccountDeletionPort` — phrase mismatch (client-side) returns `ProfileError.Delete.PhraseMismatch` without calling the port; signed-out session returns `ProfileError.Session.SignedOut`; port `Ok` → use case `Ok`; port `Err(Deletion.OwnerReassignFailed)` → `ProfileError.Delete.OwnerReassignFailed`.
- `ProfileViewModelTest` (extend, `commonTest`, `UnconfinedTestDispatcher` + Turbine): on `DeleteDialogConfirm` with a matching phrase and a fake port returning `Ok`, assert (a) `RecordingAnalyticsTracker` recorded exactly `AccountDeleted`, then a `setUserId(null)` + `resetData()`, and (b) `SignOutPort.signOut()` was invoked. On port `Err`, assert `deleteError` is set and **no** sign-out / analytics-reset fired (the user keeps their session to retry).

## 15. Konsist / arch tests

`KonsistRulesTest` (`:core:domain`) still passes: the `AccountDeletionError` re-group uses only `kotlin.stdlib` + the in-module `Result`; no Firebase/Android/Compose enters `:core:domain`. The new `FirebaseAccountDeletionPort` lives in `:feature:auth/data/firebase/` (the adapter layer) — the only place GitLive Functions types may appear, same as `FirebaseImageUrlResolver` in `:core:data`. No feature-to-feature dependency is introduced: the function does the cross-crew work server-side, so `:feature:auth` never imports `:feature:crew` (the owned-crew reassignment is TypeScript, not Kotlin). No new arch rule is required.

## 16. Order of work (for the implementation plan)

1. **`functions/`** — write `deleteAccount.ts` (core + helpers + `onCall`), export from `index.ts`, write `deleteAccount.test.ts`; `pnpm --dir functions test` green, `pnpm --dir functions build` (tsc) green.
2. **`:core:domain`** — re-group `AccountDeletionError` (drop `Ownership.OwnerOfActiveCrew`, add `Deletion.OwnerReassignFailed`); add `AnalyticsEvent.AccountDeleted`. Run `:core:domain:testAndroidHostTest` (incl. `AnalyticsTaxonomyTest` + Konsist).
3. **`:feature:auth` domain/error** — update `ProfileError.Delete` + both `AccountDeletionError.toProfileError()` and `ProfileError.toStringKey()`; update `ProfileErrorToStringKeyTest`.
4. **`:feature:auth` data** — add `FirebaseAccountDeletionPort`, delete `StubAccountDeletionPort`, swap the Koin binding in `AuthModule.kt`.
5. **`:feature:auth` presentation** — extend `ProfileViewModel.doDeleteAccount()` `Ok` branch (analytics event + reset + `signOut`); add the `analytics` ctor param; switch the Koin binding to explicit `viewModel { ProfileViewModel(…, analytics = get()) }`; update `DeleteMyAccountUseCaseTest` + `ProfileViewModelTest`.
6. **i18n** — repurpose `auth_delete_account_error_ownership` copy in en/es (§9).
7. Run the full host-test set (per CLAUDE.md "Build, run, test") + `:androidApp:assembleDebug`; `:shared:linkDebugFrameworkIosSimulatorArm64`. Quote the green output.
8. **Deploy** (MANUAL, §17) — functions, then any new index, then rules-noop.
9. Add a "Recent decisions (2026-06-14) — Account deletion" entry to `CLAUDE.md` (what/why/how) **after** the change lands and is verified — specs are pre-implementation; the CLAUDE.md entry is the post-implementation carry-forward.

## 17. Risks

- **Partial deletion / idempotency.** A network or quota failure mid-cascade can leave some data deleted and some not. Mitigation: every delete tolerates not-found (`ignoreNotFound`, `recursiveDelete` is safe on missing paths), `deleteAuthUser` runs **last**, and the function is safe to re-run — a second invocation over a half-deleted account converges to fully-deleted. The client surfaces `Backend.Unavailable` (retryable) on any non-terminal failure, and the user's session is still valid (Auth not yet deleted) so they *can* retry. Acceptance: re-run safety is asserted by the idempotent-re-run vitest case (§14.1).
- **Auth-vs-Firestore ordering.** If `deleteUser` ran *first* and Firestore then failed, the user would be locked out with orphaned data and no way to retry (no session). We delete Auth strictly last and abort before it on any data failure. The window where Auth is deleted but a trailing blob delete fails is harmless (the blob is reclaimed on the next run *or* stays as an un-referenced object — a storage-cost leak, not a privacy leak, since no doc references it; a periodic orphan-sweep is deferred).
- **`ratings[uid]` on others' meals is not directly queryable.** Step #4 can't `collectionGroup`-query a map-key existence cheaply, so it iterates the meals of the crews `U` belongs to. For tiny crews (≤ 8 members, bounded meals/day) this is fine; if a crew has a very large meal history the per-meal scan is the slowest part of the cascade. Mitigation: bound the scan to crews `U` is a member of (already required for #5), tolerate timeouts by re-run. Worst case a residual vote on someone else's meal survives one extra retry — a minor aggregate-count drift, not a privacy leak (the vote is keyed by `U` but carries no PII).
- **Owned-crew edge cases.** (a) `members` map malformed / `joinedAt` missing → reassignment can't pick a deterministic owner → `aborted` → retryable, account preserved. (b) Owner is *also* the only remaining member after others were concurrently removed mid-cascade → treated as sole-member → crew deleted (correct). (c) Two crews where reassignment partially succeeds (crew A reassigned, crew B aborts) → the function throws on B before `deleteUser`; a re-run re-processes A (idempotent — A's reassignment already dropped `U`, so `memberCrews(uid)` no longer returns A) and retries B. Acceptance: the reassign-failure vitest case asserts `deleteUser` is never reached.
- **`deleteUser` for a user who re-authenticated elsewhere.** Firebase `deleteUser` is admin-side and unconditional — it does not require the user's recent re-auth (unlike the client `user.delete()` API, which can throw `requires-recent-login`). Using the Admin SDK in the function sidesteps that entire failure mode. Confirm the function's service account has the `firebaseauth.users.delete` IAM permission (default for the Functions runtime service account — note if a custom SA is in use).
- **MANUAL store + infra steps** (not codeable here): (1) declare the in-app **Account Deletion URL** / data-deletion method in **App Store Connect** (App Privacy → Account Deletion) and **Google Play Console** (Data safety → "Provide a way for users to request that their data is deleted"); Play also wants a public deletion-request URL for users who deleted the app — point it at a short doc describing the in-app path. (2) `pnpm dlx firebase-tools deploy --only functions --project foodrats-de4ec` (then any `firestore:indexes`, then `firestore:rules` is a no-op) — deploy order per `docs/cicd-runbook.md`. (3) Verify the Functions runtime SA has `firebaseauth.users.delete`. (4) On-device smoke after deploy: sign in → publish a meal → delete account → confirm SignIn lands, and that re-signing in creates a *fresh* `accounts/{uid}` (the old data is gone).
