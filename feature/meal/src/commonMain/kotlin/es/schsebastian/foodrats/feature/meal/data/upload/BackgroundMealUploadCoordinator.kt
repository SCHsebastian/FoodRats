package es.schsebastian.foodrats.feature.meal.data.upload

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.analytics.PublishSource
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.telemetry.NoopCrashReporter
import es.schsebastian.foodrats.feature.meal.data.queue.DraftRetryRunner
import es.schsebastian.foodrats.feature.meal.data.queue.uploadErrorKey
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinator that runs meal uploads as background work that survives the composer leaving the
 * back stack and the process being killed mid-upload.
 *
 * SINGLE EXECUTOR: when the durable [draftQueue] is bound (production), it is the *only* publisher.
 * [enqueueDraftUpload] just enqueues the draft; [DraftRetryRunner] drains it exactly once
 * (deterministic `MealId` → idempotent) and owns retries, offline backoff, connectivity-resume and
 * the true publish-outcome analytics. This coordinator then merely MIRRORS the queue into the
 * single-upload [status] flow (the Feed "uploading" indicator) and the [queue] aggregate. There is
 * no second in-process publish — an earlier design ran [doUpload] concurrently with the queue and
 * the loser's create rejection deleted the live plate (the "image vanishes" bug).
 *
 * FALLBACK: when no durable queue is bound (e.g. unit tests), [doUpload] is the in-process executor,
 * resumed across process death via the [Keys.MealUploadPending] flag.
 *
 * Both [MealUploadCoordinator] (write side) and [MealUploadProgressPort] (read side for Feed +
 * Stats) are implemented here so Koin holds a single instance.
 */
class BackgroundMealUploadCoordinator(
    private val repository: MealRepository,
    private val publishMeal: PublishMealUseCase,
    private val prefs: AppPreferences,
    private val scheduler: MealUploadScheduler,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
    private val crashReporter: CrashReporter = NoopCrashReporter,
    // Offline-first durable queue (roadmap §5.2). Nullable so existing direct
    // construction in tests stays green; when bound, the coordinator durably
    // enqueues each draft and lets [retryRunner] drain it on connectivity-return,
    // and publishes the aggregate count through [queue].
    private val draftQueue: DraftQueuePort? = null,
    private val retryRunner: DraftRetryRunner? = null,
) : MealUploadCoordinator, MealUploadProgressPort, QueuedUploadActionsPort {

    private val _status = MutableStateFlow<MealUploadStatus>(MealUploadStatus.Idle)
    override val status: StateFlow<MealUploadStatus> = _status.asStateFlow()

    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
            dispatchers.default +
            CoroutineExceptionHandler { _, t ->
                FrLog.w("MealUpload", t) { "upload scope uncaught: ${t.message}" }
                _status.value = MealUploadStatus.Failed(errorKey = ERROR_UNKNOWN)
            },
    )

    /**
     * Cross-feature aggregate of the durable queue (pending / terminal-failed),
     * for the feed top bar. Derived live from [DraftQueuePort.observe]; the empty
     * snapshot when no durable queue is bound (e.g. in a unit test).
     */
    override val queue: StateFlow<MealUploadQueueSnapshot> =
        (draftQueue?.observe()?.map { DraftRetryRunner.snapshotOf(it) } ?: MutableStateFlow(MealUploadQueueSnapshot.EMPTY))
            .stateIn(scope, SharingStarted.Eagerly, MealUploadQueueSnapshot.EMPTY)

    private val mutex = Mutex()
    private var inFlight: Job? = null

    /**
     * A [DraftQueuePort.enqueue] write failure (durable-queue DataStore IO) never produces a
     * [QueuedDraft] entry — there's nothing for [deriveStatus] to see — so without this the
     * meal would silently vanish: the composer has already navigated away on
     * [MealUploadCoordinator.enqueueDraftUpload]'s fire-and-forget contract, and no error is
     * ever surfaced. Sticky until the next successful enqueue (or the queue itself reports
     * real work), combined into [status] below so it isn't clobbered by an unrelated queue
     * re-emission.
     */
    private val enqueueFailure = MutableStateFlow<MealUploadStatus.Failed?>(null)

    init {
        val q = draftQueue
        if (q != null) {
            // Durable mode (single executor). Mirror the queue into the single-upload [status]
            // flow so the Feed "uploading" indicator works without this coordinator publishing.
            // The retry runner drains persisted Pending entries on launch (process-death resume)
            // and on connectivity-return — no legacy single-flag resume needed.
            combine(q.observe().map(::deriveStatus), enqueueFailure) { queueStatus, failure ->
                if (failure != null && queueStatus == MealUploadStatus.Idle) failure else queueStatus
            }
                .distinctUntilChanged()
                .onEach { _status.value = it }
                .launchIn(scope)
            retryRunner?.start(scope)
        } else {
            // Fallback (no durable queue): auto-resume the in-process single-flag upload after
            // process death — the mutex serialises it with the WorkManager-spawned retry.
            scope.launch {
                val pending = runCatching { prefs.observe(Keys.MealUploadPending).first() }.getOrNull()
                if (pending == true) {
                    FrLog.d("MealUpload") { "resuming pending upload from previous session" }
                    runUploadIfPending()
                }
            }
        }
    }

    override fun enqueueDraftUpload() {
        scope.launch {
            val q = draftQueue
            if (q != null) {
                // SINGLE executor: durably enqueue the draft and let [DraftRetryRunner] publish it
                // exactly once. The durable entry (incl. plate bytes) is itself the process-death /
                // offline safety net, so no separate in-process publish + pending flag is needed —
                // running both is what previously let the loser delete the live plate.
                val draft = runCatching { repository.observeDraft().first() }.getOrNull()
                if (draft != null) {
                    when (val r = q.enqueue(draft)) {
                        is Result.Ok -> enqueueFailure.value = null
                        is Result.Err -> {
                            // The durable write failed — the draft never entered the queue, so
                            // DraftRetryRunner will never see it and no retry will ever happen.
                            // Surface it as a terminal failure (see [enqueueFailure]) and report
                            // it: this is otherwise a totally silent dropped meal.
                            crashReporter.recordNonFatal(
                                IllegalStateException("draft enqueue failed: ${r.error}"),
                                tag = "meal-upload-enqueue",
                            )
                            enqueueFailure.value = MealUploadStatus.Failed(errorKey = r.error.uploadErrorKey())
                        }
                    }
                }
                scheduler.schedule()
            } else {
                // Fallback: in-process single-flag upload (no durable queue bound, e.g. unit tests).
                mutex.withLock {
                    prefs.set(Keys.MealUploadPending, true)
                    if (inFlight?.isActive == true) return@withLock
                    inFlight = scope.launch { doUpload() }
                }
                scheduler.schedule()
            }
        }
    }

    /**
     * Re-arm every terminal `Failed(retryable = false)` entry back to `Pending`
     * (roadmap §5.2 feed-top-bar retry). Flipping the status to Pending makes the
     * [retryRunner]'s queue-observer trigger a fresh drain pass; the deterministic
     * `MealId` means the re-publish overwrites, never duplicates.
     *
     * Also covers an [enqueueFailure] (the durable *write itself* failed, so the draft
     * never became a queue entry in the first place — there's nothing here to re-arm).
     * `repository`'s draft is untouched by a failed enqueue, so re-running
     * [enqueueDraftUpload] retries against the same still-composed draft.
     */
    override suspend fun retryFailed() {
        val q = draftQueue ?: return
        if (enqueueFailure.value != null) {
            enqueueFailure.value = null
            enqueueDraftUpload()
        }
        val terminal = runCatching { q.observe().first() }.getOrNull().orEmpty()
            .filter { (it.status as? QueuedDraftStatus.Failed)?.retryable == false }
        for (entry in terminal) {
            q.updateStatus(entry.id, QueuedDraftStatus.Pending)
        }
    }

    /**
     * Drop every terminal `Failed(retryable = false)` entry from the queue — the
     * user has abandoned those plates (roadmap §5.2 feed-top-bar dismiss). Also
     * clears a pending [enqueueFailure] (the user abandoned that plate too).
     */
    override suspend fun dismissFailed() {
        val q = draftQueue ?: return
        enqueueFailure.value = null
        val terminal = runCatching { q.observe().first() }.getOrNull().orEmpty()
            .filter { (it.status as? QueuedDraftStatus.Failed)?.retryable == false }
        for (entry in terminal) {
            q.remove(entry.id)
        }
    }

    /**
     * Re-entry point used by Android's [MealUploadWorker]. In durable mode the worker drains the
     * queue itself via [DraftRetryRunner.runOnce], so this legacy single-flag path has nothing to
     * do — report "done" so it doesn't gate the worker's success on a path that never runs. In the
     * fallback (no durable queue) it joins/runs the in-process upload and reports whether it
     * succeeded, so the worker can choose `Result.success` vs `Result.retry`.
     */
    suspend fun resumeFromBackgroundWorker(): Boolean {
        if (draftQueue != null) return true
        val deferred = mutex.withLock {
            val pending = runCatching { prefs.observe(Keys.MealUploadPending).first() }.getOrNull()
            if (pending != true) return true
            if (inFlight?.isActive == true) inFlight
            else scope.launch { doUpload() }.also { inFlight = it }
        }
        deferred?.join()
        return _status.value is MealUploadStatus.Succeeded
    }

    /**
     * Map the durable-queue contents to the single-upload [status] (durable mode): any entry still
     * working → Uploading; otherwise the most recent terminal failure → Failed; otherwise Idle.
     * A successful publish removes its entry, so the empty queue reads as Idle (the Feed only acts
     * on Uploading; the [queue] aggregate carries failed/pending counts for the top bar).
     */
    private fun deriveStatus(entries: List<QueuedDraft>): MealUploadStatus {
        val anyActive = entries.any { e ->
            when (val s = e.status) {
                QueuedDraftStatus.Pending, QueuedDraftStatus.Uploading -> true
                is QueuedDraftStatus.Failed -> s.retryable
                QueuedDraftStatus.Succeeded -> false
            }
        }
        if (anyActive) return MealUploadStatus.Uploading
        val terminal = entries.firstNotNullOfOrNull { (it.status as? QueuedDraftStatus.Failed)?.takeIf { f -> !f.retryable } }
        return if (terminal != null) MealUploadStatus.Failed(terminal.errorKey) else MealUploadStatus.Idle
    }

    private suspend fun runUploadIfPending() {
        mutex.withLock {
            if (inFlight?.isActive == true) return
            inFlight = scope.launch { doUpload() }
        }
    }

    private suspend fun doUpload() {
        _status.value = MealUploadStatus.Uploading
        val draft = repository.observeDraft().first()
        if (draft == null) {
            // No draft to publish — clear the marker so we don't loop forever.
            prefs.clear(Keys.MealUploadPending)
            scheduler.cancel()
            _status.value = MealUploadStatus.Failed(errorKey = ERROR_NO_DRAFT)
            return
        }
        when (val r = publishMeal(draft)) {
            is Result.Ok -> {
                prefs.clear(Keys.MealUploadPending)
                scheduler.cancel()
                _status.value = MealUploadStatus.Succeeded
                // meal_published fires on the TRUE outcome (publish Result Ok), not at enqueue —
                // this is the funnel-conversion + publishing-depth event. No PII (slot/counts only;
                // slot is optional and emitted as "none" when absent).
                analytics.track(
                    AnalyticsEvent.MealPublished(
                        slot = draft.slot,
                        ingredientCount = draft.ingredients.size,
                        hasDescription = draft.description.value.isNotBlank(),
                        audienceCrewCount = draft.audienceCrewIds.size,
                        source = PublishSource.UNKNOWN,
                    ),
                )
                // No local streak nudge is scheduled here. The server-side `streakNudge` Cloud
                // Function (roadmap §1.1) is now the single "go post" channel — a per-crew,
                // social-proof push to non-posters. Scheduling the local DailyInactivityWorker too
                // would double-nudge the user every day, so the client schedule call was removed.
                // The worker + LocalReminderScheduler + StreakNotificationPort all remain defined
                // and bindable, just no longer triggered — re-adding one call here restores it.
            }
            // Idempotency-success: this draft's (crew, day, slot) is already published — the
            // durable-queue drain (or a prior attempt) won the publish race. The upload goal is
            // met, so clear the pending flag and report success instead of leaving it set to spin
            // a WorkManager retry loop that can only ever re-hit AlreadyPostedToday.
            is Result.Err if r.error == MealError.Publish.AlreadyPostedToday -> {
                prefs.clear(Keys.MealUploadPending)
                scheduler.cancel()
                _status.value = MealUploadStatus.Succeeded
            }
            is Result.Err -> {
                analytics.track(AnalyticsEvent.MealPublishFailed(errorLeaf = r.error::class.simpleName ?: "Unknown"))
                // Leave the pending flag set so WorkManager backoff retries
                // (Android) or the next-launch resume (iOS) try again.
                _status.value = MealUploadStatus.Failed(errorKey = r.error.uploadErrorKey())
            }
        }
    }

    private companion object {
        const val ERROR_UNKNOWN = "meal.upload.unknown"
        const val ERROR_NO_DRAFT = "meal.upload.no_draft"
    }
}
