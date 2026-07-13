package es.schsebastian.foodrats.feature.meal.data.upload

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.PublishSource
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadStatus
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.data.queue.DraftQueueLocalStore
import es.schsebastian.foodrats.feature.meal.data.queue.DraftQueueRepository
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraft
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import es.schsebastian.foodrats.feature.meal.domain.usecase.PublishMealUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * BUG 1 (HIGH, offline-edge/error-handling): a failing durable-queue [DraftQueuePort.enqueue]
 * write used to be silently discarded — the meal vanished with no error, no retry, no
 * CrashReporter signal, while the composer had already navigated away on the
 * `UploadEnqueued` effect. See `BackgroundMealUploadCoordinator.enqueueDraftUpload`.
 */
class BackgroundMealUploadCoordinatorTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private class NoopScheduler : MealUploadScheduler {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }

    private class RecordingCrashReporter : CrashReporter {
        val tags = mutableListOf<String?>()
        override fun recordNonFatal(throwable: Throwable, tag: String?) {
            tags += tag
        }
        override fun log(message: String) = Unit
    }

    private fun realQueue(): DraftQueueRepository = DraftQueueRepository(
        DraftQueueLocalStore(AppPreferences(FakeDataStore())),
        FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
        dispatchers,
    )

    private fun draft() = MealDraft(
        audienceCrewIds = setOf((CrewId.of("crew-1") as Result.Ok).value),
        authorId = (AccountId.of("acc-1") as Result.Ok).value,
        day = MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
        plates = listOf(Plate(byteArrayOf(9, 8, 7))),
        dish = (es.schsebastian.foodrats.core.domain.meal.DishName.of("Pasta") as Result.Ok).value,
        description = Description.EMPTY,
        slot = MealSlot.Lunch,
    )

    @Test
    fun failed_durable_enqueue_surfaces_a_terminal_failed_status_and_reports_to_crash_reporter() = runTest {
        val repository = FakeMealRepository()
        repository.saveDraft(draft())
        // A DraftQueuePort whose enqueue ALWAYS fails, simulating a durable-write (DataStore
        // IO) failure — the composer's draft never actually enters the queue.
        val failingQueue = object : DraftQueuePort by realQueue() {
            override suspend fun enqueue(draft: MealDraft): Result<QueuedDraft, MealError> =
                Result.Err(MealError.Publish.PublishUnavailable)
        }
        val crashReporter = RecordingCrashReporter()
        val coordinator = BackgroundMealUploadCoordinator(
            repository = repository,
            publishMeal = PublishMealUseCase(
                repository,
                FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
                TimeZone.UTC,
            ),
            prefs = AppPreferences(FakeDataStore()),
            scheduler = NoopScheduler(),
            dispatchers = dispatchers,
            crashReporter = crashReporter,
            draftQueue = failingQueue,
            retryRunner = null,
        )

        coordinator.enqueueDraftUpload()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            crashReporter.tags.contains("meal-upload-enqueue"),
            "a failed durable enqueue must be reported, not silently swallowed",
        )
        assertEquals(
            MealUploadStatus.Failed(errorKey = "meal.error.publishUnavailable"),
            coordinator.status.value,
            "the feed's uploading/failed indicator must reflect the failure instead of staying Idle forever",
        )
    }

    @Test
    fun retrying_a_failed_enqueue_re_attempts_against_the_still_composed_draft() = runTest {
        val repository = FakeMealRepository()
        repository.saveDraft(draft())
        var shouldFail = true
        val realQ = realQueue()
        val flakyQueue = object : DraftQueuePort by realQ {
            override suspend fun enqueue(draft: MealDraft): Result<QueuedDraft, MealError> =
                if (shouldFail) Result.Err(MealError.Publish.PublishUnavailable) else realQ.enqueue(draft)
        }
        val coordinator = BackgroundMealUploadCoordinator(
            repository = repository,
            publishMeal = PublishMealUseCase(
                repository,
                FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
                TimeZone.UTC,
            ),
            prefs = AppPreferences(FakeDataStore()),
            scheduler = NoopScheduler(),
            dispatchers = dispatchers,
            draftQueue = flakyQueue,
            retryRunner = null,
        )

        coordinator.enqueueDraftUpload()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(MealUploadStatus.Failed(errorKey = "meal.error.publishUnavailable"), coordinator.status.value)

        shouldFail = false
        coordinator.retryFailed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            1,
            realQ.observe().first().size,
            "retryFailed must re-attempt the enqueue against the still-composed draft, " +
                "landing it in the real queue once the write stops failing",
        )
    }

    /** The FALLBACK (no durable queue) publisher — `doUpload()` — also stamps `meal_published.source`
     *  via the same [toPublishSource] mapping the durable [DraftRetryRunner] path uses. This is the
     *  in-process executor unit tests exercise; production always binds a durable queue, but the
     *  mapping must stay correct here too since it's shared code (`List<Plate>.toPublishSource()`). */
    @Test
    fun fallback_upload_stamps_gallery_publish_source_from_the_plate() = runTest {
        val repository = FakeMealRepository()
        repository.saveDraft(draft().copy(plates = listOf(Plate(byteArrayOf(9, 8, 7), source = PlateSource.Gallery))))
        val analytics = RecordingAnalyticsTracker()
        val coordinator = BackgroundMealUploadCoordinator(
            repository = repository,
            publishMeal = PublishMealUseCase(
                repository,
                FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
                TimeZone.UTC,
            ),
            prefs = AppPreferences(FakeDataStore()),
            scheduler = NoopScheduler(),
            dispatchers = dispatchers,
            analytics = analytics,
            draftQueue = null,
            retryRunner = null,
        )

        coordinator.enqueueDraftUpload()
        testDispatcher.scheduler.advanceUntilIdle()

        val event = analytics.events.single() as AnalyticsEvent.MealPublished
        assertEquals(PublishSource.GALLERY, event.source)
    }

    @Test
    fun fallback_upload_stamps_camera_publish_source_from_the_plate() = runTest {
        val repository = FakeMealRepository()
        repository.saveDraft(draft()) // draft()'s plate defaults to PlateSource.Camera
        val analytics = RecordingAnalyticsTracker()
        val coordinator = BackgroundMealUploadCoordinator(
            repository = repository,
            publishMeal = PublishMealUseCase(
                repository,
                FixedClock(Instant.parse("2026-06-14T10:00:00Z")),
                TimeZone.UTC,
            ),
            prefs = AppPreferences(FakeDataStore()),
            scheduler = NoopScheduler(),
            dispatchers = dispatchers,
            analytics = analytics,
            draftQueue = null,
            retryRunner = null,
        )

        coordinator.enqueueDraftUpload()
        testDispatcher.scheduler.advanceUntilIdle()

        val event = analytics.events.single() as AnalyticsEvent.MealPublished
        assertEquals(PublishSource.CAMERA, event.source)
    }
}
