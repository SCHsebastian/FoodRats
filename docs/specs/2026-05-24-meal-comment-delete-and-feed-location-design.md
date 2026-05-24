# Meal/Comment deletion + Feed location — design

Status: PROPOSED (2026-05-24). Author: Sebas + Claude.

## 0. Context / why this exists

Two capabilities believed to be already shipped were found **never implemented** in
the repository (verified across all branches, the reflog, and the specs/plans on
2026-05-24):

- The feed/detail UI never renders a meal's location. GPS is captured at compose
  time and persisted (`Meal.coordinates`, `MealDto.latitude/longitude`), but
  `FeedMealUi`/`toFeedUi` drop it and no map/location component exists in any commit.
- There is no meal or comment deletion. `MealRepository.delete` is a no-op stub
  (`Result.success(Unit)`), there is no `DeleteMealUseCase`/`DeleteCommentUseCase`,
  no delete affordance in the UI, and `firestore.rules` has **no** delete rule for
  meals while comments are explicitly `// No update, no delete — comments are immutable.`

This spec covers building both, from scratch.

## 1. Permission model (single source of truth)

A principal may delete a meal or a comment iff **either**:
- they are the **author** of that meal/comment, OR
- they are the **owner of the crew** the meal belongs to (`Crew.ownerId`).

Owner-deletes-anything is the moderation path; author-deletes-own is self-service.
Owner identity is the existing `Crew.ownerId: AccountId` (same field used by
`CrewSettings` rename/delete). No new "admin" concept.

## 2. Feature A — Deletion

### 2.1 Domain (`feature/meal`)

- `MealError` gains:
  ```
  sealed interface Delete : MealError {
      data object NotAuthorOrOwner : Delete   // authorization
      data object NotFound         : Delete
      data object Unavailable      : Delete
  }
  ```
  (Sealed-interface + data-object per repo convention; add matching `MealStringKey`
  leaves + entries in `MealErrorToStringKey` mapper + exhaustiveness test.)
- `CommentError` gains a parallel `sealed interface Delete { NotAuthorOrOwner; NotFound; Unavailable }`
  with the same i18n + mapper + test treatment in `:feature:feed` (where `CommentError`
  is surfaced) / `:core:domain` (where it's declared).
- `MealRepository.delete` signature changes to carry the crew + caller context the
  Firestore path and the authorization check need:
  ```
  suspend fun delete(crewId: CrewId, mealId: MealId): Result<Unit, MealError.Delete>
  ```
  (crewId is required: meals live under `crews/{crewId}/meals/{mealId}`. It is also
  derivable from the `MealId` prefix, but passing it explicitly is clearer and matches
  the rate() signature.)
- New `MealCommentPort.delete(crewId, mealId, commentId)` (or a `CommentWritePort`
  extension) returning `Result<Unit, CommentError.Delete>`.
- New use cases (pure orchestration, one per intent):
  - `DeleteMealUseCase(mealRepo, crewRead, session)` — loads the meal author + crew
    owner, checks the §1 rule, calls `mealRepo.delete`.
  - `DeleteCommentUseCase(commentPort, crewRead, session)` — same shape.

### 2.2 Cross-feature owner lookup (the one non-trivial wiring)

The feed/detail layer currently knows `crewId` (via `ActiveCrewProvider`) and the
viewer (`SessionProvider`) but **not** the crew's `ownerId`. Features can't depend on
each other, so we add a `:core:domain` port:
```
interface CrewOwnerPort { fun observeOwner(crewId: CrewId): Flow<AccountId?> }
```
implemented in `:feature:crew` (reads `crews/{id}.ownerId`, which `CrewFirestoreDataSource`
already projects) and consumed by the delete use cases + the feed/detail VMs to decide
whether to show the delete affordance. (Alternative: do the authorization purely in the
Firestore rule and let the client always attempt; but we want the UI to only show
"Delete" when permitted, so the client needs ownerId.)

### 2.3 Data (`feature/meal`)

- `MealFirestoreDataSource.deleteMeal(crewId, mealId)` → `crews/{crewId}/meals/{mealId}.delete()`.
  Also delete the `comments` subcollection? Firestore does not cascade. Options:
  (a) leave orphan comments (cheap, but they linger), or (b) a Cloud Function
  `onMealDelete` that recursively deletes the subcollection (clean). **Recommend (b)**
  — there is already a `functions/` backend; add a trigger. For v1 we can ship (a) and
  file the function as follow-up if you prefer speed.
- `CommentFirestoreDataSource.deleteComment(crewId, mealId, commentId)` → `.delete()`.
- `FirebaseMealRepository.delete` stops being a stub; one `withContext(dispatchers.io)`,
  maps vendor exceptions via `MealErrorMapper.mapDelete`.

### 2.4 UI

- **Feed card / detail (meal):** an overflow "⋯" affordance on `FrFeedMealCard` /
  `MealDetailScreen`, visible only when `canDelete` (author or owner). Tapping opens a
  confirm dialog (`FrConfirmDialog`, mirroring `DeleteCrewConfirmDialog`). On confirm →
  `DeleteMeal` intent. `FeedMealUi`/`MealDetail` state gains `canDelete: Boolean`.
- **Comment row:** same overflow + confirm on `FrCommentRow`; `CommentRowUi` gains
  `canDelete: Boolean`. `MealDetailIntent.DeleteComment(commentId)`.
- All strings via `resolve(StringKey)` (new keys: `DeleteMealCta`, `DeleteMealConfirmTitle/Body`,
  `DeleteCommentCta`, confirm copy, EN + ES).

### 2.5 Firestore rules (`firestore.rules`) — **destructive, requires deploy**

Add to `crews/{crewId}/meals/{mealId}`:
```
allow delete: if request.auth != null
              && ( request.auth.uid == resource.data.authorId
                   || request.auth.uid == get(/databases/$(database)/documents/crews/$(crewId)).data.ownerId );
```
Replace the comments `// No update, no delete` with:
```
allow delete: if request.auth != null
              && ( request.auth.uid == resource.data.authorId
                   || request.auth.uid == get(/databases/$(database)/documents/crews/$(crewId)).data.ownerId );
```
Deploy: `pnpm dlx firebase-tools deploy --only firestore:rules --project foodrats-de4ec`.
(Note: the doc field is `authorId` on meals; confirm the comment doc's author field name
in `CommentDto` and match it.)

### 2.6 Tests

- `DeleteMealUseCaseTest` / `DeleteCommentUseCaseTest`: author allowed, owner allowed,
  stranger rejected (`NotAuthorOrOwner`), not-found path.
- `MealErrorToStringKeyTest` / comment equivalent: exhaustiveness over the new `Delete` leaves.
- VM tests: `canDelete` computed correctly for author vs owner vs other; delete intent
  flows error → banner.

## 3. Feature B — Location in the feed (mini static map)

### 3.1 Data → UI plumbing (no external dependency)

- `FeedMealUi` gains `latitude: Double?`, `longitude: Double?` (or a `LocationUi?`);
  `toFeedUi` copies `meal.coordinates`. Same for the meal-detail state.

### 3.2 Render — **open decision (needs your input)**

A static-map tile requires a maps provider with an **API key** (Google Static Maps /
Mapbox) — has cost and a credential that can't be provisioned automatically. Two paths:

- **B1 (keyless, ship now):** `FrLocationChip` — pin icon + coordinates (optionally a
  place name if a free reverse-geocode is acceptable). No key, no cost. Leaves a clean
  seam (`FrStaticMap`) to swap in the tile later.
- **B2 (static tile):** `FrStaticMap` loads `https://maps.googleapis.com/maps/api/staticmap?...&key=KEY`
  via Coil `AsyncImage` (already wired). Key injected like `googleServerClientId`
  (gradle property / secret, **never** committed). You provide the key + enable billing.

Recommendation: ship **B1** now, upgrade to **B2** when a key exists. The UI seam is
identical so B2 is a localized change.

## 4. Sequencing

1. Deletion (self-contained, no key): domain → port → data → rules → UI → tests.
2. Location plumbing + B1 chip.
3. B2 static tile once a maps key is provided.

## 5. Open decisions for sign-off

1. Maps key: B1 keyless now, or B2 (you provide key)?
2. Orphan comments on meal delete: ship (a) leave-orphans now, or (b) add the
   `onMealDelete` Cloud Function up front?
3. Build/test verification: I can't run the full Gradle suite within this environment's
   per-command time cap — I'll write code + unit tests and run them via a background
   Gradle run polled across calls, but the authoritative green run may need to be on your
   Mac. OK?
