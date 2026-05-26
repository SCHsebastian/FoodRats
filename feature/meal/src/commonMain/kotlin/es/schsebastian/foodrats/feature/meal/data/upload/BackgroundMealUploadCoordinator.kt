package es.schsebastian.foodrats.feature.meal.data.upload

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.meal.MealUploadCoordinator
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val streakNotifications: StreakNotificationPort,
    private val prefs: AppPreferences,
    private val scheduler: MealUploadScheduler,
) : MealUploadCoordinator, MealUploadProgressPort {

    private val _status = MutableStateFlow<MealUploadStatus>(MealUploadStatus.Idle)
    override val status: StateFlow<MealUploadStatus> = _status.asStateFlow()

    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, t ->
                FrLog.w("MealUpload", t) { "upload scope uncaught: ${t.message}" }
                _status.value = MealUploadStatus.Failed(errorKey = ERROR_UNKNOWN)
            },
    )

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
    }

    override fun enqueueDraftUpload() {
        scope.launch {
            mutex.withLock {
                prefs.set(Keys.MealUploadPending, true)
                if (inFlight?.isActive == true) return@withLock
                inFlight = scope.launch { doUpload() }
            }
            scheduler.schedule()
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
                // Streak nudge is best-effort — the upload already succeeded.
                runCatching { streakNotifications.scheduleStreakNudge() }
                    .onFailure { FrLog.w("MealUpload", it) { "streak nudge skipped: ${it.message}" } }
            }
            is Result.Err -> {
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
