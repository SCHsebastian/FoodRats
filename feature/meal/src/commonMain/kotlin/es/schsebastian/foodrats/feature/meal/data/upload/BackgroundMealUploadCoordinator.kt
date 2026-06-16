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
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.meal.data.queue.DraftRetryRunner
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinator that runs meal uploads as background work that survives:
 *   1. The composer screen leaving the back stack (in-process app scope).
 *   2. The process itself being killed mid-upload — the [Keys.MealUploadPending]
 *      flag is persisted to DataStore on enqueue and stays set until the
 *      publish succeeds. On Android, a [MealUploadScheduler] enqueues a
 *      WorkManager job in addition to the in-process scope; on iOS the
 *      scheduler is a no-op and the auto-resume on the next [init]
 *      handles the "app got killed" case.
 *
 * Both [MealUploadCoordinator] (write side) and [MealUploadProgressPort]
 * (read side for Feed + Stats) are implemented here so Koin holds a single
 * instance.
 */
class BackgroundMealUploadCoordinator(
    private val repository: MealRepository,
    private val publishMeal: PublishMealUseCase,
    private val prefs: AppPreferences,
    private val scheduler: MealUploadScheduler,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
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

    init {
        // Auto-resume after process death: if the persisted flag is set, the
        // previous session's upload didn't finish, so kick it off now. On
        // Android this races (harmlessly) with the WorkManager-spawned retry —
        // the mutex serialises them and the second caller sees inFlight active.
        scope.launch {
            val pending = runCatching { prefs.observe(Keys.MealUploadPending).first() }.getOrNull()
            if (pending == true) {
                FrLog.d("MealUpload") { "resuming pending upload from previous session" }
                runUploadIfPending()
            }
        }
        // Wire the durable-queue retry runner's triggers (connectivity-return +
        // new pending entries) onto the app-lifetime upload scope.
        retryRunner?.start(scope)
    }

    override fun enqueueDraftUpload() {
        scope.launch {
            // Durably enqueue the current draft so an offline / process-death
            // session never loses the plate; the retry runner drains it idempotently
            // (same deterministic MealId → overwrite, never duplicate). This runs in
            // addition to the immediate single-upload fast path below — a race is a
            // harmless overwrite of the same Firestore doc.
            draftQueue?.let { q ->
                val draft = runCatching { repository.observeDraft().first() }.getOrNull()
                if (draft != null) q.enqueue(draft)
            }
            mutex.withLock {
                prefs.set(Keys.MealUploadPending, true)
                if (inFlight?.isActive == true) return@withLock
                inFlight = scope.launch { doUpload() }
            }
            scheduler.schedule()
        }
    }

    /**
     * Re-arm every terminal `Failed(retryable = false)` entry back to `Pending`
     * (roadmap §5.2 feed-top-bar retry). Flipping the status to Pending makes the
     * [retryRunner]'s queue-observer trigger a fresh drain pass; the deterministic
     * `MealId` means the re-publish overwrites, never duplicates.
     */
    override suspend fun retryFailed() {
        val q = draftQueue ?: return
        val terminal = runCatching { q.observe().first() }.getOrNull().orEmpty()
            .filter { (it.status as? QueuedDraftStatus.Failed)?.retryable == false }
        for (entry in terminal) {
            q.updateStatus(entry.id, QueuedDraftStatus.Pending)
        }
    }

    /**
     * Drop every terminal `Failed(retryable = false)` entry from the queue — the
     * user has abandoned those plates (roadmap §5.2 feed-top-bar dismiss).
     */
    override suspend fun dismissFailed() {
        val q = draftQueue ?: return
        val terminal = runCatching { q.observe().first() }.getOrNull().orEmpty()
            .filter { (it.status as? QueuedDraftStatus.Failed)?.retryable == false }
        for (entry in terminal) {
            q.remove(entry.id)
        }
    }

    /**
     * Re-entry point used by Android's [MealUploadWorker]. Joins any
     * in-process upload (so we don't run the publish twice) and otherwise
     * runs it. Returns whether the upload eventually succeeded — the worker
     * uses that to decide between `Result.success` and `Result.retry`.
     */
    suspend fun resumeFromBackgroundWorker(): Boolean {
        val deferred = mutex.withLock {
            val pending = runCatching { prefs.observe(Keys.MealUploadPending).first() }.getOrNull()
            if (pending != true) return true
            if (inFlight?.isActive == true) inFlight
            else scope.launch { doUpload() }.also { inFlight = it }
        }
        deferred?.join()
        return _status.value is MealUploadStatus.Succeeded
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
                // this is the funnel-conversion + publishing-depth event. No PII (slot/counts only).
                draft.slot?.let { slot ->
                    analytics.track(
                        AnalyticsEvent.MealPublished(
                            slot = slot,
                            ingredientCount = draft.ingredients.size,
                            hasDescription = draft.description.value.isNotBlank(),
                            audienceCrewCount = draft.audienceCrewIds.size,
                            source = PublishSource.UNKNOWN,
                        ),
                    )
                }
                // No local streak nudge is scheduled here. The server-side `streakNudge` Cloud
                // Function (roadmap §1.1) is now the single "go post" channel — a per-crew,
                // social-proof push to non-posters. Scheduling the local DailyInactivityWorker too
                // would double-nudge the user every day, so the client schedule call was removed.
                // The worker + LocalReminderScheduler + StreakNotificationPort all remain defined
                // and bindable, just no longer triggered — re-adding one call here restores it.
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

/**
 * Token the presentation layer maps to a `MealStringKey` via
 * `MealErrorMapper.uploadErrorKeyToStringKey`. Domain doesn't know about
 * `StringKey`, so we pass an opaque marker.
 */
private fun MealError.uploadErrorKey(): String = when (this) {
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
