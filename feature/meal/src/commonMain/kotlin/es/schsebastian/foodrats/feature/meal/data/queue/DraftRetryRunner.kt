package es.schsebastian.foodrats.feature.meal.data.queue

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.core.domain.time.SystemClock
import es.schsebastian.foodrats.feature.meal.data.upload.toPublishSource
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueueTransitions
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.CancellationException
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
import kotlinx.datetime.TimeZone

/**
 * Background retry runner for the offline-first publish queue (roadmap §5.2).
 *
 * Pure-Kotlin orchestration shared by both platforms — the platform pieces are
 * the durable wakeup (Android WorkManager `NetworkType.CONNECTED`; iOS next
 * foreground) and the [ConnectivityPort]. The runner itself only decides
 * *what* to publish and *when to give up*, using the pure [DraftRetryPolicy] and
 * [DraftQueueTransitions].
 *
 * Drain pass ([runOnce]) — for each entry whose [QueuedDraftStatus] is
 * [QueuedDraftStatus.Pending] (or a retryable [QueuedDraftStatus.Failed] flipped
 * back to Pending after its backoff):
 *  1. [DraftQueuePort.markUploading] (CAS Pending→Uploading; skip if not claimed)
 *     → publish via [MealRepository.publish] (idempotent: re-publishing overwrites
 *     the deterministic `MealId.forDaySlot` document, so a retry after a
 *     partial/crashed success never duplicates),
 *  2. `Ok` → [DraftQueuePort.remove] (reconcile-on-success),
 *  3. `Err` → [DraftQueuePort.markFailed] with the policy-derived `retryable`;
 *     if still retryable, schedule a backed-off re-attempt
 *     ([DraftRetryPolicy.nextDelay]) that flips the entry back to Pending; else
 *     leave it terminal for the user.
 * A drain pass holds a [Mutex] so connectivity + enqueue triggers can't run two
 * passes concurrently (the deterministic id makes a double-run harmless, but
 * serialising is cleaner and avoids redundant Firestore writes).
 *
 * BUG FIX (orphaned Uploading entries). The CAS claim durably transitions a row
 * Pending→Uploading BEFORE [MealRepository.publish] is called. If the process
 * dies, or the coroutine carrying that call is cancelled, mid-publish — the row
 * would be left Uploading forever: [runOnce] only ever picks up
 * [QueuedDraftStatus.Pending] entries, so the publish is never retried and the
 * "waiting to publish (N)" feed bar counts it as outstanding permanently, with no
 * Retry/Dismiss CTA (those exist only for terminal Failed). Two things close the
 * gap: [start] reconciles any stale Uploading row back to Pending at boot
 * ([reconcileStaleUploading]), and [attempt] wraps the publish call in a
 * try/catch that restores Pending on cancellation and treats any other thrown
 * [Throwable] as a retryable failure through the normal markFailed/backoff path.
 *
 * [snapshot] derives the cross-feature [MealUploadQueueSnapshot] from the queue
 * so the coordinator can republish it through `MealUploadProgressPort.queue`.
 */
class DraftRetryRunner(
    private val queue: DraftQueuePort,
    private val publish: MealRepository,
    private val connectivity: ConnectivityPort,
    private val policy: DraftRetryPolicy = DraftRetryPolicy(),
    // The durable queue is the single publish executor (the coordinator no longer publishes
    // directly), so the true publish outcome is emitted HERE — `meal_published` on success,
    // `meal_publish_failed` only when a draft is given up on (terminal). Default Noop keeps the
    // direct-construction tests green. NO PII (slot/counts only).
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
    // Used ONLY to re-stamp a stale `MealDraft.day` on the very first publish attempt — see
    // the day-rollover note on [attempt]. Defaults keep existing direct-construction call
    // sites green.
    private val clock: Clock = SystemClock(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val mutex = Mutex()

    /**
     * Wire the runner's triggers onto [scope] (the app-lifetime upload scope):
     *  - drain whenever connectivity rises to online,
     *  - drain whenever a new Pending entry appears.
     * Backoff re-attempts are launched per-entry on the same [scope].
     */
    fun start(scope: CoroutineScope) {
        // BUG FIX: reconcile any Uploading row left over from a previous process/coroutine death
        // BEFORE wiring the drain triggers below. See [reconcileStaleUploading].
        scope.launch { reconcileStaleUploading() }

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
     * BUG FIX (orphaned Uploading entries): [attempt]'s CAS claim ([DraftQueuePort.markUploading])
     * durably transitions a row Pending→Uploading BEFORE calling [MealRepository.publish]. If the
     * process dies, or the coroutine carrying that call is cancelled, mid-publish — the row is
     * left Uploading forever: drains only ever pick up [QueuedDraftStatus.Pending] entries
     * ([runOnce]'s `filter { it.status is QueuedDraftStatus.Pending }`), so the draft is never
     * retried and the "waiting to publish" bar counts it as outstanding permanently.
     *
     * Called once from [start], before the connectivity/pending-count triggers are wired: by
     * construction there is no drain in flight yet at that point (this is the first thing the
     * single runner instance does), so any [QueuedDraftStatus.Uploading] row found now is
     * necessarily a hangover from a previous process — the single-claim-owner invariant the CAS
     * gives us makes flipping it back to Pending here safe. Runs under [mutex] so it can never
     * race a concurrently-launched [runOnce] (e.g. a fast connectivity trigger firing before this
     * coroutine is scheduled).
     */
    private suspend fun reconcileStaleUploading() = mutex.withLock {
        val stale = queue.observe().first().filter { it.status is QueuedDraftStatus.Uploading }
        for (entry in stale) {
            FrLog.w("DraftQueue") {
                "reconciling stale Uploading row ${entry.id.value} back to Pending at startup"
            }
            reportIfErr("reconcileStaleUploading", queue.updateStatus(entry.id, QueuedDraftStatus.Pending))
        }
    }

    /**
     * Report a failed queue status write so it is at least observable — a silently-failed
     * [DraftQueuePort.updateStatus] / [DraftQueuePort.markFailed] write desyncs the queue (e.g. an
     * entry stays claimed Uploading, or keeps a stale attempt count) with nothing surfacing it.
     * [DraftRetryRunner] has no [es.schsebastian.foodrats.core.domain.telemetry.CrashReporter]
     * dependency, so this only logs — the existing failure-reporting pattern in this class.
     */
    private fun reportIfErr(op: String, result: Result<*, MealError>) {
        if (result is Result.Err) {
            FrLog.w("DraftQueue") { "queue status write '$op' failed: ${result.error}" }
        }
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

    /**
     * Uniform outcome of a single [MealRepository.publish] attempt inside [attempt] — a modeled
     * `Result<Meal, MealError>` and a thrown [Throwable] are both normalized into this before the
     * shared markFailed/backoff handling runs, so a thrown Throwable (which has no [MealError] to
     * map through `uploadErrorKey()`) still carries its own diagnostic-only `errorKey`.
     */
    private sealed interface PublishAttempt {
        data object Published : PublishAttempt
        data object AlreadyPosted : PublishAttempt
        data class Failed(val errorKey: String, val analyticsLeaf: String) : PublishAttempt
    }

    private suspend fun attempt(entry: QueuedDraft, scope: CoroutineScope?) {
        // Re-stamp a stale `day` (e.g. drafted just before midnight, drained after
        // connectivity returns) to *today* — but ONLY on the very first attempt
        // (`attemptCount == 0`). FirebaseMealRepository.publish fans a draft out to
        // EVERY audience crew in a loop keyed by `MealId.forDayToken(crewId, author,
        // draft.day, token)`; once any attempt has run, a partial fan-out may already
        // have landed some crews under the OLD day's deterministic id. Restamping again
        // on a retry would target a DIFFERENT id for the remaining crews and duplicate
        // the meal instead of idempotently overwriting it — so after attempt 1, every
        // later retry of this entry must keep using whichever day attempt 1 actually
        // used. [DraftQueuePort.markUploading]'s draft override persists the corrected
        // day back onto the entry so it stays consistent across retries.
        val today = MealDay.today(clock, zone)
        val restamped = if (entry.attemptCount == 0 && entry.draft.day != today) {
            entry.draft.copy(day = today)
        } else {
            null
        }
        // BUG FIX: CAS claim — only proceed if we actually transitioned the entry from Pending.
        // A concurrent drain (or an entry no longer Pending) must not be published twice.
        when (val claimed = queue.markUploading(entry.id, restamped)) {
            is Result.Err -> {
                FrLog.w("DraftQueue") { "markUploading persistence error for ${entry.id.value}; skipping" }
                return
            }
            is Result.Ok -> if (!claimed.value) {
                FrLog.d("DraftQueue") { "CAS miss for ${entry.id.value}; skipping (another drain owns it)" }
                return
            }
        }
        val draftToPublish = restamped ?: entry.draft
        // BUG FIX (orphaned Uploading entries, part 2): the CAS claim above already committed this
        // entry to Uploading. If `publish.publish` throws or is cancelled — process death, a
        // lifecycle-scoped `scope` being torn down mid-call — the row must not be left claimed
        // forever. A thrown CancellationException restores the row to Pending and rethrows (never
        // swallow cancellation); any other unexpected Throwable is normalized into [PublishAttempt]
        // just like a modeled `Result.Err`, so it flows through the exact same markFailed/backoff
        // handling below rather than leaking the claim — except it is NOT mapped through
        // `MealError.uploadErrorKey()` (it isn't a modeled MealError); it gets its own
        // diagnostic-only errorKey instead.
        val outcome: PublishAttempt = try {
            when (val r = publish.publish(draftToPublish)) {
                is Result.Ok -> PublishAttempt.Published
                // AlreadyPostedToday is idempotency-success for a durable queue: this draft's
                // (crew, day, slot) is already published (e.g. a prior drain already posted it),
                // so the goal is met. Reconcile by removing — never mark it failed (which would
                // surface a phantom "upload failed" in the feed bar or spin a retry) and don't
                // re-emit meal_published (the original publish already did).
                is Result.Err if r.error == MealError.Publish.AlreadyPostedToday -> PublishAttempt.AlreadyPosted
                is Result.Err -> PublishAttempt.Failed(
                    errorKey = r.error.uploadErrorKey(),
                    analyticsLeaf = r.error::class.simpleName ?: "Unknown",
                )
            }
        } catch (e: CancellationException) {
            FrLog.w("DraftQueue") {
                "publish cancelled for ${entry.id.value}; restoring to Pending"
            }
            reportIfErr("updateStatus-cancelledRestore", queue.updateStatus(entry.id, QueuedDraftStatus.Pending))
            throw e
        } catch (e: Throwable) {
            FrLog.w("DraftQueue") {
                "publish threw for ${entry.id.value}; treating as retryable rather than leaking the Uploading claim: ${e.message}"
            }
            PublishAttempt.Failed(errorKey = "meal.error.publishThrew", analyticsLeaf = "Throwable")
        }
        when (outcome) {
            PublishAttempt.Published -> {
                FrLog.d("DraftQueue") { "published queued draft ${entry.id.value}; removing" }
                queue.remove(entry.id)
                trackPublished(draftToPublish)
            }
            PublishAttempt.AlreadyPosted -> {
                FrLog.d("DraftQueue") { "queued draft ${entry.id.value} already posted; removing" }
                queue.remove(entry.id)
            }
            is PublishAttempt.Failed -> {
                val newAttemptCount = entry.attemptCount + 1
                val failed = DraftQueueTransitions.onFailure(newAttemptCount, outcome.errorKey, policy)
                reportIfErr("markFailed-retryable", queue.markFailed(entry.id, failed.errorKey, failed.retryable))
                // Emit the failure event only when we give up (terminal) — a retryable attempt
                // isn't a publish-failed outcome yet, and firing per-retry would over-count.
                if (!failed.retryable) {
                    analytics.track(AnalyticsEvent.MealPublishFailed(errorLeaf = outcome.analyticsLeaf))
                }
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

    /**
     * `meal_published` fires on the TRUE publish outcome (a queue entry reconciled as Ok), the
     * funnel-conversion + publishing-depth event. No PII — slot + counts only.
     */
    private fun trackPublished(draft: MealDraft) {
        analytics.track(
            AnalyticsEvent.MealPublished(
                slot = draft.slot,
                ingredientCount = draft.ingredients.size,
                hasDescription = draft.description.value.isNotBlank(),
                audienceCrewCount = draft.audienceCrewIds.size,
                source = draft.plates.toPublishSource(),
                photoCount = draft.plates.size,
            ),
        )
    }

    /** Flip [id] back to Pending after [delayMs] so the next drain picks it up. */
    private fun scheduleRetry(scope: CoroutineScope, id: QueueEntryId, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            reportIfErr("updateStatus-scheduledRetry", queue.updateStatus(id, QueuedDraftStatus.Pending))
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
    MealError.Validation.TooManyPhotos   -> "meal.error.tooManyPhotos"
    MealError.Validation.OutOfRange      -> "meal.error.outOfRange"
    MealError.Read.Unauthorized          -> "meal.error.readUnauthorized"
    MealError.Read.CrewNotFound          -> "meal.error.readCrewNotFound"
    MealError.Read.NotFound              -> "meal.error.readNotFound"
    MealError.Location.PermissionDenied  -> "meal.error.locationPermission"
    MealError.Location.Unavailable       -> "meal.error.locationUnavailable"
    MealError.Location.Timeout           -> "meal.error.locationTimeout"
}
