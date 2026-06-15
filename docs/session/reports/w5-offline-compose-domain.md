# Report — w5-offline-compose-domain

**Task:** DOMAIN layer for offline-first meal compose (roadmap §5.2): a durable queued-draft model,
a `DraftQueuePort` enqueue/observe/update/dequeue contract, a pure retry/backoff policy, the
idempotency-key strategy, and the pure status-transition logic. Domain-layer only — persistence,
WorkManager/iOS background tasks, and connectivity monitoring belong to `w5-offline-compose-data`;
the feed top-bar UI belongs to `w5-offline-compose-presentation`.

**Status:** DONE — verified green.

## Prior work check

None on disk (no `QueuedDraft` / `DraftQueuePort` / `DraftRetryPolicy` / `backoff` references existed
in `feature/meal` or `core/domain`). Started fresh.

## What I read

- `docs/roadmap/2026-06-14-feature-roadmap.md` §5.2 (queue model, retry with connectivity
  constraints, idempotency via deterministic ids, "extend `MealUploadProgressPort` states" for the
  top bar). The roadmap file is the live copy; `docs/specs/…-feature-roadmap.md` does not exist.
- `feature/meal/domain/model/MealDraft.kt` (carries `audienceCrewIds`, `plate`, `dish`, `slot`,
  `ingredients`, `detectedDishSlug`, `cuisine`).
- `feature/meal/domain/usecase/PublishMealUseCase.kt`, `domain/error/MealError.kt`,
  `domain/repository/MealRepository.kt`.
- `feature/meal/data/upload/BackgroundMealUploadCoordinator.kt` + `:core:domain`'s
  `MealUploadCoordinator` / `MealUploadProgressPort` / `MealUploadStatus`.
- `core/domain/.../meal/MealId.kt` — the deterministic id `MealId.forDaySlot(crewId, authorId, day,
  slot)` already exists and is what publish uses today (per-crew document id + Storage path).

## Key finding: idempotency is already solved

The publish path derives a **deterministic** per-crew meal id from `(crewId, authorId, day, slot)`
via `MealId.forDaySlot(...)`. Re-publishing the same draft therefore overwrites the same Firestore
document (and the same deterministic Storage blob path) instead of creating a duplicate. So the
offline queue needs **no separate idempotency token to persist or transmit** — the meal id *is* the
idempotency key. The queue's own id (`QueueEntryId`) is a *queue-tracking* id (stable across retries
so the data layer can address/observe/update/remove one entry), independent of meal identity. One
queue entry whose audience spans N crews maps to N deterministic meal ids.

## Files created (all `:feature:meal`, commonMain + commonTest)

**Domain model:**
- `domain/model/QueuedDraft.kt` — `QueuedDraft(id, draft, status, attemptCount, createdAt,
  lastAttemptAt)` + `@JvmInline value class QueueEntryId(value)` (non-blank invariant). KDoc spells
  out the queue-id-vs-meal-id distinction.
- `domain/model/QueuedDraftStatus.kt` — sealed interface, leaves `Pending`, `Uploading`,
  `Succeeded` (data objects) + `Failed(errorKey: String, retryable: Boolean)` (data class). `errorKey`
  is an opaque i18n token mirroring `MealUploadStatus.Failed.errorKey` (domain doesn't know i18n).

**Domain ports / policy (in `domain/queue/`):**
- `DraftQueuePort.kt` — `enqueue / observe / updateStatus / markUploading / markFailed / remove`,
  all `Result<…, MealError>` except `observe(): Flow<List<QueuedDraft>>`. Documents that the
  cross-feature top-bar read goes through the existing `:core:domain` `MealUploadProgressPort`, NOT
  this port (keeps the queue meal-internal; feed must not depend on `:feature:meal`).
- `DraftRetryPolicy.kt` — pure class. `shouldRetry(attemptCount)`, `isExhausted(attemptCount)`,
  `nextDelay(attemptCount): Duration?` (exponential `initialBackoff * multiplier^(n-1)` capped at
  `maxBackoff`, `null` when exhausted). Defaults: maxAttempts=5, 30s→60s→120s→240s, cap 1h.
  Constructor validates its inputs.
- `IdempotencyKeys.kt` — `MealDraft.idempotencyKeys(): Set<MealId>` — pure/total; the per-crew
  deterministic `MealId.forDaySlot(...)` set (empty for no-slot or empty-audience). The testable
  "key stability" surface.
- `DraftQueueTransitions.kt` — pure `object`: `beginAttempt()`→Uploading, `onSuccess()`→Succeeded,
  `onFailure(attemptCount, errorKey, policy)`→`Failed(retryable = policy.shouldRetry(attemptCount))`.
  Single source of truth so Android + iOS runners transition identically.

**Tests (commonTest, `domain/queue/`):**
- `DraftRetryPolicyTest.kt` (9) — first attempt, exponential growth, cap clamp, retry-while-budget,
  max-attempts terminal, not-exhausted-before-max, single-attempt give-up, invalid construction,
  defaults match documented schedule.
- `IdempotencyKeysTest.kt` (5) — key == deterministic id per crew, one key per crew (multi-crew),
  **stability across calls** (the retry-safety property), no-slot → empty, empty-audience → empty.
- `DraftQueueTransitionsTest.kt` (5) — beginAttempt, success terminal, failure-within-budget
  retryable, failure-at-max terminal, full Pending→Uploading→Failed(retryable)→…→Succeeded lifecycle.

## Decisions (where §5.2 was silent — documented in code KDoc)

1. **No new `MealError` leaf.** Queue failures reuse the existing `MealError.Publish.*` from the
   publish path and surface to the user via the opaque `errorKey` token already used by
   `MealUploadStatus.Failed`. Adding a queue-specific error would have been ceremony with no payload.
   (So no `*ErrorToStringKeyTest` change was needed.)
2. **`DraftQueuePort` lives in `:feature:meal`, not `:core:domain`.** The queue is meal-owned. The
   feed top-bar count is served by the **existing** `:core:domain` `MealUploadProgressPort` /
   `MealUploadStatus` (roadmap §5.2 explicitly says "extend `MealUploadProgressPort` states") — the
   data task derives an aggregate from the queue and publishes it there. This avoids leaking the full
   per-entry model across the feature boundary and avoids feed→meal coupling.
3. **`QueueEntryId` ≠ meal id.** Queue-tracking id only; idempotency rides on `MealId.forDaySlot`.
4. **Retry policy is deterministic (no jitter).** Jitter belongs to the platform runner; a
   deterministic policy is what makes it unit-testable. `attemptCount` is 1-based = "attempts already
   failed". `nextDelay` returns `null` (not a sentinel) when exhausted.
5. **Status transition logic extracted to a pure `object`** rather than left as prose, so the
   transition table is itself tested and shared verbatim by both platform runners.

## Verify

```
./gradlew :feature:meal:testAndroidHostTest
```
Last 3 lines:
```
> Task :feature:meal:testAndroidHostTest

BUILD SUCCESSFUL in 4s
```
New tests confirmed run + green (from `build/test-results/`):
`DraftRetryPolicyTest tests="9" failures="0" errors="0"`,
`IdempotencyKeysTest tests="5" failures="0" errors="0"`,
`DraftQueueTransitionsTest tests="5" failures="0" errors="0"`.

No `:core:domain` change → no `:core:domain:testAndroidHostTest` run needed.

## Blockers

None.

## What the next tasks must implement — see handoff

`docs/session/handoffs/w5-offline-compose-domain.md`.
