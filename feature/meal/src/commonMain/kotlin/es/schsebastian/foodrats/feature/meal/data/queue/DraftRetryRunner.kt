package es.schsebastian.foodrats.feature.meal.data.queue

import es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueueTransitions
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background retry runner for the offline-first publish queue (roadmap §5.2).
 *
 * Pure-Kotlin orchestration shared by both platforms — the platform pieces are
 * the durable wakeup (Android WorkManager `NetworkType.CONNECTED`; iOS next
 * foreground) and the [ConnectivityMonitor]. The runner itself only decides
 * *what* to publish and *when to give up*, using the pure [DraftRetryPolicy] and
 * [DraftQueueTransitions].
 *
 * Drain pass ([runOnce]) — for each entry whose [QueuedDraftStatus] is
 * [QueuedDraftStatus.Pending] (or a retryable [QueuedDraftStatus.Failed] flipped
 * back to Pending after its backoff):
 *  1. [DraftQueuePort.markUploading] → publish via [MealRepository.publish]
 *     (idempotent: re-publishing overwrites the deterministic `MealId.forDaySlot`
 *     document, so a retry after a partial/crashed success never duplicates),
 *  2. `Ok` → [DraftQueuePort.remove] (reconcile-on-success),
 *  3. `Err` → [DraftQueuePort.markFailed] with the policy-derived `retryable`;
 *     if still retryable, schedule a backed-off re-attempt
 *     ([DraftRetryPolicy.nextDelay]) that flips the entry back to Pending; else
 *     leave it terminal for the user.
 * A drain pass holds a [Mutex] so connectivity + enqueue triggers can't run two
 * passes concurrently (the deterministic id makes a double-run harmless, but
 * serialising is cleaner and avoids redundant Firestore writes).
 *
 * [snapshot] derives the cross-feature [MealUploadQueueSnapshot] from the queue
 * so the coordinator can republish it through `MealUploadProgressPort.queue`.
 */
class DraftRetryRunner(
    private val queue: DraftQueuePort,
    private val publish: MealRepository,
    private val connectivity: ConnectivityMonitor,
    private val policy: DraftRetryPolicy = DraftRetryPolicy(),
) {
    private val mutex = Mutex()

    /**
     * Wire the runner's triggers onto [scope] (the app-lifetime upload scope):
     *  - drain whenever connectivity rises to online,
     *  - drain whenever a new Pending entry appears.
     * Backoff re-attempts are launched per-entry on the same [scope].
     */
    fun start(scope: CoroutineScope) {
        // false→true edge of connectivity (distinctUntilChanged already applied
        // by the monitor; we drain on every `true`).
        connectivity.isOnline()
            .onEach { online -> if (online) launchDrain(scope) }
            .launchIn(scope)

        // A change in the count of drainable (Pending) entries means new work to do.
        queue.observe()
            .map { list -> list.count { it.status is QueuedDraftStatus.Pending } }
            .distinctUntilChanged()
            .onEach { pending -> if (pending > 0) launchDrain(scope) }
            .launchIn(scope)
    }

    private fun launchDrain(scope: CoroutineScope) {
        scope.launch { runOnce(scope) }
    }

    /**
     * Run a single drain pass over all currently-Pending entries. Returns `true`
     * iff the queue holds no drainable (Pending/Uploading) work afterwards — the
     * Android worker maps `true`→success, `false`→retry. [scope] is used to
     * launch per-entry backoff re-attempts; pass `null` to skip scheduling
     * (e.g. the worker, which relies on WorkManager backoff instead).
     */
    suspend fun runOnce(scope: CoroutineScope? = null): Boolean = mutex.withLock {
        val entries = queue.observe().first().filter { it.status is QueuedDraftStatus.Pending }
        for (entry in entries) {
            attempt(entry, scope)
        }
        // Undrained = anything still trying: Pending, mid-Uploading, or a *retryable*
        // Failed (it will be re-armed to Pending after backoff). A terminal
        // Failed(retryable = false) is "done" from the drainer's view — it won't
        // resolve on its own, so the worker shouldn't keep retrying for it.
        val remaining = queue.observe().first().count { e ->
            when (val s = e.status) {
                QueuedDraftStatus.Pending,
                QueuedDraftStatus.Uploading -> true
                is QueuedDraftStatus.Failed -> s.retryable
                QueuedDraftStatus.Succeeded -> false
            }
        }
        remaining == 0
    }

    private suspend fun attempt(entry: QueuedDraft, scope: CoroutineScope?) {
        queue.markUploading(entry.id)
        when (val r = publish.publish(entry.draft)) {
            is Result.Ok -> {
                FrLog.d("DraftQueue") { "published queued draft ${entry.id.value}; removing" }
                queue.remove(entry.id)
            }
            is Result.Err -> {
                val newAttemptCount = entry.attemptCount + 1
                val failed = DraftQueueTransitions.onFailure(newAttemptCount, r.error.uploadErrorKey(), policy)
                queue.markFailed(entry.id, failed.errorKey, failed.retryable)
                if (failed.retryable) {
                    val delayMs = policy.nextDelay(newAttemptCount)?.inWholeMilliseconds
                    FrLog.d("DraftQueue") {
                        "queued draft ${entry.id.value} failed (attempt $newAttemptCount); retry in ${delayMs}ms"
                    }
                    if (scope != null && delayMs != null) scheduleRetry(scope, entry.id, delayMs)
                } else {
                    FrLog.w("DraftQueue") { "queued draft ${entry.id.value} exhausted retries; terminal" }
                }
            }
        }
    }

    /** Flip [id] back to Pending after [delayMs] so the next drain picks it up. */
    private fun scheduleRetry(scope: CoroutineScope, id: QueueEntryId, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            queue.updateStatus(id, QueuedDraftStatus.Pending)
        }
    }

    companion object {
        /** Derive the cross-feature aggregate from a queue snapshot list. */
        fun snapshotOf(entries: List<QueuedDraft>): MealUploadQueueSnapshot {
            var pending = 0
            var terminalFailed = 0
            for (e in entries) {
                when (val s = e.status) {
                    QueuedDraftStatus.Pending,
                    QueuedDraftStatus.Uploading -> pending++
                    is QueuedDraftStatus.Failed -> if (s.retryable) pending++ else terminalFailed++
                    QueuedDraftStatus.Succeeded -> Unit
                }
            }
            return MealUploadQueueSnapshot(pending = pending, terminalFailed = terminalFailed)
        }
    }
}

/**
 * Map a publish [MealError] to the opaque `errorKey` token the presentation layer
 * resolves to a `MealStringKey`. The single source of truth shared by both the
 * durable-queue retry path ([DraftRetryRunner]) and the single-upload fast path
 * ([es.schsebastian.foodrats.feature.meal.data.upload.BackgroundMealUploadCoordinator]),
 * so both emit identical tokens.
 */
internal fun MealError.uploadErrorKey(): String = when (this) {
    MealError.Publish.AlreadyPostedToday -> "meal.error.alreadyPosted"
    MealError.Publish.NoSlotSelected     -> "meal.error.noSlot"
    MealError.Publish.NoCrewSelected     -> "meal.error.noCrewSelected"
    MealError.Publish.NotToday           -> "meal.error.notToday"
    MealError.Publish.PublishUnavailable -> "meal.error.publishUnavailable"
    MealError.Publish.PhotoUploadFailed  -> "meal.error.photoUploadFailed"
    MealError.Validation.Blank           -> "meal.error.blank"
    MealError.Validation.NoPhoto         -> "meal.error.noPhoto"
    MealError.Validation.TooLong         -> "meal.error.tooLong"
    MealError.Validation.DescriptionTooLong -> "meal.error.descriptionTooLong"
    MealError.Validation.TooManyIngredients -> "meal.error.tooManyIngredients"
    MealError.Validation.OutOfRange      -> "meal.error.outOfRange"
    MealError.Read.Unauthorized          -> "meal.error.readUnauthorized"
    MealError.Read.CrewNotFound          -> "meal.error.readCrewNotFound"
    MealError.Read.NotFound              -> "meal.error.readNotFound"
    MealError.Location.PermissionDenied  -> "meal.error.locationPermission"
    MealError.Location.Unavailable       -> "meal.error.locationUnavailable"
    MealError.Location.Timeout           -> "meal.error.locationTimeout"
}
