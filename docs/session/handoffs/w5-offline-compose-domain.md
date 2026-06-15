# Handoff — w5-offline-compose-domain

Domain layer for offline-first meal compose is in place (roadmap §5.2). All types are in
`:feature:meal` commonMain. Package: `es.schsebastian.foodrats.feature.meal.domain`.

## Types you build against

### `domain/model/QueuedDraft.kt`
```kotlin
data class QueuedDraft(
    val id: QueueEntryId,        // stable client-generated, constant across retries
    val draft: MealDraft,        // the composed draft (audience, Plate, dish, slot, ingredients…)
    val status: QueuedDraftStatus,
    val attemptCount: Int = 0,   // attempts already FAILED (0 before first attempt)
    val createdAt: Instant,      // kotlin.time.Instant
    val lastAttemptAt: Instant? = null,
)

@JvmInline value class QueueEntryId(val value: String)  // require(value.isNotBlank())
```
`QueueEntryId` is a **queue-tracking** id, NOT the meal id. Generate it in the data layer (platform
UUID).

### `domain/model/QueuedDraftStatus.kt`
```kotlin
sealed interface QueuedDraftStatus {
    data object Pending : QueuedDraftStatus
    data object Uploading : QueuedDraftStatus
    data object Succeeded : QueuedDraftStatus              // terminal → dequeue
    data class Failed(val errorKey: String, val retryable: Boolean) : QueuedDraftStatus
}
```
`errorKey` is the same opaque i18n token style as `MealUploadStatus.Failed.errorKey` (e.g.
`"meal.upload.unknown"`). `retryable = false` is the terminal give-up state.

### `domain/queue/DraftQueuePort.kt`
```kotlin
interface DraftQueuePort {
    suspend fun enqueue(draft: MealDraft): Result<QueuedDraft, MealError>
    fun observe(): Flow<List<QueuedDraft>>
    suspend fun updateStatus(id: QueueEntryId, status: QueuedDraftStatus): Result<Unit, MealError>
    suspend fun markUploading(id: QueueEntryId): Result<Unit, MealError>
    suspend fun markFailed(id: QueueEntryId, errorKey: String, retryable: Boolean): Result<Unit, MealError>
    suspend fun remove(id: QueueEntryId): Result<Unit, MealError>
}
```

### `domain/queue/DraftRetryPolicy.kt` (pure)
```kotlin
class DraftRetryPolicy(
    val maxAttempts: Int = 5,
    val initialBackoff: Duration = 30.seconds,
    val multiplier: Double = 2.0,
    val maxBackoff: Duration = 1.hours,   // (60*60).seconds
) {
    fun shouldRetry(attemptCount: Int): Boolean        // attemptCount in 1 until maxAttempts
    fun isExhausted(attemptCount: Int): Boolean        // attemptCount >= maxAttempts
    fun nextDelay(attemptCount: Int): Duration?        // exp backoff capped; null when exhausted
}
```
`attemptCount` is **1-based** = "attempts already failed". After the 1st failure, `nextDelay(1) = 30s`.

### `domain/queue/DraftQueueTransitions.kt` (pure object)
```kotlin
DraftQueueTransitions.beginAttempt()  // = Uploading
DraftQueueTransitions.onSuccess()     // = Succeeded
DraftQueueTransitions.onFailure(attemptCount, errorKey, policy)  // = Failed(errorKey, retryable = policy.shouldRetry(attemptCount))
```

### `domain/queue/IdempotencyKeys.kt` (pure)
```kotlin
fun MealDraft.idempotencyKeys(): Set<MealId>   // one MealId.forDaySlot(crew, author, day, slot) per audience crew
```

## Idempotency contract (READ THIS — it shapes the data task)

There is **no separate idempotency key**. Each per-crew publish uses the deterministic id
`MealId.forDaySlot(crewId, authorId, day, slot)` (already what `FirebaseMealRepository.publish`
does). A retried publish of the same `QueuedDraft.draft` targets the exact same Firestore document id
and the same deterministic Storage path, so the write **overwrites, never duplicates**. Therefore the
retry runner can safely re-run a publish that may have *partially* succeeded (crashed after writing
crew A's copy but before crew B's) — re-writing A is a no-op-equivalent overwrite. Use
`MealDraft.idempotencyKeys()` if you need to reconcile/verify which docs an entry maps to.

## w5-offline-compose-data MUST implement

1. **`DraftQueuePort` over durable local storage** (DataStore or SQLDelight — pick one; the meal
   feature already has a `MealDraftLocalStore` in `data/local/` for the *single* in-flight draft —
   the queue is a separate, multi-entry store). Persist the full `QueuedDraft` incl. the `Plate`
   bytes (or a file ref) so it survives process death. Exactly **one** `withContext(dispatchers.io)`
   per public method (CLAUDE.md rule).
2. **The retry runner** — Android WorkManager job with `NetworkType.CONNECTED` constraint; iOS
   background task / URLSession background. The runner:
   - picks up `Pending` entries, calls `markUploading`, runs the publish (reuse
     `PublishMealUseCase` / `MealRepository.publish` — idempotent per above),
   - on `Result.Ok` → `remove(id)` (reconcile-on-success),
   - on `Result.Err` → increment attempt, call `DraftQueueTransitions.onFailure(newAttemptCount,
     errorKey, policy)` and `markFailed(...)`; if `policy.shouldRetry(newAttemptCount)`, schedule the
     next attempt after `policy.nextDelay(newAttemptCount)` (back to `Pending`); else leave the
     terminal `Failed(retryable=false)`.
   - Map the publish `MealError` to an `errorKey` the same way
     `BackgroundMealUploadCoordinator.uploadErrorKey()` already does (reuse those tokens).
3. **Feed top-bar feed:** derive an aggregate from `observe()` (e.g. a count of
   `Pending` + `Uploading` entries, plus any terminal `Failed`) and publish it through the **existing**
   `:core:domain` `MealUploadProgressPort` / `MealUploadStatus` — **extend `MealUploadStatus` states**
   if a plain count isn't enough (roadmap §5.2 says to extend it). Do NOT add a feed→meal dep; the
   cross-feature read stays on the `:core:domain` port.
4. **Wire Koin:** bind `DraftQueuePort`, `DraftRetryPolicy` (defaults are fine), and the scheduler;
   add to the feature's `*ModuleVerifyTest` `extraTypes` as needed.
5. **Decide enqueue trigger:** likely `MealUploadCoordinator.enqueueDraftUpload()` (or a new method)
   enqueues a `QueuedDraft` when offline / on first failure, instead of the current single-flag
   `Keys.MealUploadPending` path. Coordinate with the existing `BackgroundMealUploadCoordinator` so
   the two upload paths don't double-publish (its mutex + deterministic id already make a double-run
   harmless, but converging on the queue is cleaner).
6. **Tests:** queue persistence round-trip, retry scheduling against `DraftRetryPolicy`, idempotent
   re-publish (re-running a publish doesn't create a 2nd doc), reconcile-on-success removal.

## w5-offline-compose-presentation MUST implement

- Observe the aggregate queue state via the `:core:domain` `MealUploadProgressPort` (extended
  `MealUploadStatus`) in the feed top bar — show pending/uploading count and a retry/dismiss
  affordance for terminal `Failed(retryable=false)` entries.
- All user-visible strings via `MealStringKey` (or `FeedStringKey`) → `resolve(...)`; map the
  `errorKey` tokens to string keys (extend the existing `MealErrorMapper.uploadErrorKeyToStringKey`).
- MVI single source of truth; no parallel `MutableStateFlow`.

## Not in scope (confirmed not done here)

No local DB/DataStore, no WorkManager, no connectivity monitoring, no UI, no Koin wiring, no
`:core:domain` change, no new `MealError` leaf.
