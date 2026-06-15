package es.schsebastian.foodrats.feature.meal.domain.queue

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain port for the durable offline-first publish queue (roadmap §5.2).
 *
 * Declared in `:feature:meal` (the queue is meal-owned). The data task
 * (`w5-offline-compose-data`) implements it over local persistence
 * (DataStore / SQLDelight) + the platform retry runner (Android WorkManager
 * with `NetworkType.CONNECTED`; iOS background task / URLSession background).
 * The actual connectivity-gated retry loop lives in the data layer; this port
 * only declares the enqueue / observe / update-status / dequeue contract.
 *
 * CROSS-FEATURE READ. The feed top bar's "pending uploads" count
 * (`w5-offline-compose-presentation`) is NOT served by this port — that crosses
 * into `:feature:feed`, which must not depend on `:feature:meal`. The existing
 * `MealUploadProgressPort` / `MealUploadStatus` in `:core:domain` is the
 * cross-context read surface; the data task derives the aggregate queue state
 * (e.g. a count of [QueuedDraftStatus.Pending] / [QueuedDraftStatus.Uploading]
 * entries) and publishes it through that port, extending `MealUploadStatus`
 * states as needed. Keeping [DraftQueuePort] meal-internal avoids leaking the
 * full per-entry model across the feature boundary.
 *
 * IDEMPOTENCY is a property of the publish path, not this port: re-publishing an
 * entry's [MealDraft] overwrites the deterministic `MealId.forDaySlot(...)`
 * document rather than duplicating it, so [markUploading] → publish → [remove]
 * (or a crash-and-retry in between) can never create two meals. See
 * [QueuedDraft] for the full rationale.
 */
interface DraftQueuePort {

    /**
     * Durably enqueue [draft] for offline-first publishing and return the
     * created entry (with its generated [QueueEntryId], [QueuedDraftStatus.Pending]
     * status, `attemptCount = 0`, and `createdAt`). The entry survives process
     * death; the platform retry runner picks it up when connectivity allows.
     */
    suspend fun enqueue(draft: MealDraft): Result<QueuedDraft, MealError>

    /**
     * Observe the full queue (e.g. ordered by `createdAt`). Emits a fresh list
     * on every change so the data layer's retry runner and any in-feature
     * surface can react. Empty when nothing is queued.
     */
    fun observe(): Flow<List<QueuedDraft>>

    /**
     * Update the lifecycle [status] of the entry identified by [id], also
     * bumping `attemptCount` / `lastAttemptAt` as appropriate for the
     * transition (the implementation owns that bookkeeping; callers pass the
     * intended status). No-op-safe if the entry is already gone.
     */
    suspend fun updateStatus(id: QueueEntryId, status: QueuedDraftStatus): Result<Unit, MealError>

    /** Convenience transition to [QueuedDraftStatus.Uploading] before a publish attempt. */
    suspend fun markUploading(id: QueueEntryId): Result<Unit, MealError>

    /**
     * Record a failed attempt: set [QueuedDraftStatus.Failed] with [errorKey] and
     * [retryable], and increment `attemptCount`. The runner consults
     * [DraftRetryPolicy] (with the new `attemptCount`) to decide whether to
     * schedule another attempt (retryable) or leave the entry in a terminal
     * failed state for the user.
     */
    suspend fun markFailed(
        id: QueueEntryId,
        errorKey: String,
        retryable: Boolean,
    ): Result<Unit, MealError>

    /**
     * Remove the entry [id] from the queue. Called after a successful publish
     * (reconcile-on-success) or when the user dismisses a terminally-failed
     * entry. No-op-safe if already removed.
     */
    suspend fun remove(id: QueueEntryId): Result<Unit, MealError>
}
