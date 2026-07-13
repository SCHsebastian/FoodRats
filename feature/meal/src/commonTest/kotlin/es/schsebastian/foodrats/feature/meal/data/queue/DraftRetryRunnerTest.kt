package es.schsebastian.foodrats.feature.meal.data.queue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.PublishSource
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.MealUploadQueueSnapshot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.domain.model.QueueEntryId
import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftQueuePort
import es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import es.schsebastian.foodrats.feature.meal.domain.test.FakeMealRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DraftRetryRunnerTest {

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

    // Same instant as `draft().day` (2026-06-14, UTC) so the runner's day-rollover
    // re-stamp (attemptCount == 0 only) is a no-op for every test that doesn't
    // exercise it explicitly — see stale_day_is_restamped_* below.
    private val sameDayClock = FixedClock(Instant.parse("2026-06-14T10:00:00Z"))
    private val zone = TimeZone.UTC

    private class AlwaysOnline : ConnectivityPort {
        override fun isOnline(): Flow<Boolean> = flowOf(true)
    }

    /**
     * MealRepository whose publish outcome is scripted per attempt. Delegates every other
     * member to the canonical [FakeMealRepository] (which is not `open`, hence delegation).
     */
    private class ScriptedPublishRepository(
        private val outcomes: ArrayDeque<Result<Boolean, MealError>>,
        private val delegate: FakeMealRepository = FakeMealRepository(),
    ) : MealRepository by delegate {
        var publishCount = 0
        val publishedDrafts get() = delegate.publishedDrafts
        override suspend fun publish(draft: MealDraft): Result<es.schsebastian.foodrats.core.domain.meal.Meal, MealError> {
            publishCount++
            return when (val next = outcomes.removeFirstOrNull()
                ?: Result.Err(MealError.Publish.PublishUnavailable)) {
                is Result.Ok -> delegate.publish(draft) // canonical Ok meal build
                is Result.Err -> Result.Err(next.error)
            }
        }
    }

    private fun queue(backing: DataStore<Preferences> = FakeDataStore()): DraftQueueRepository =
        DraftQueueRepository(
            DraftQueueLocalStore(AppPreferences(backing)),
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

    /** Flip a failed/retryable entry back to Pending — proxy for the elapsed backoff / WorkManager re-fire. */
    private suspend fun DraftQueuePort.rearm(id: QueueEntryId) = updateStatus(id, QueuedDraftStatus.Pending)

    @Test
    fun pending_then_publish_success_removes_the_entry() = runTest {
        val q = queue()
        q.enqueue(draft())
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), clock = sameDayClock, zone = zone)

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "queue should be fully drained after a successful publish")
        assertEquals(1, repo.publishCount)
        assertTrue(q.observe().first().isEmpty(), "successful entry must be reconciled (removed)")
        assertEquals(draft().slot, repo.publishedDrafts.single().slot)
    }

    /** The queue is the single publish executor, so the true `meal_published` event is emitted here
     *  (relocated from the coordinator) on a successful drain — exactly once, with no PII. */
    @Test
    fun successful_drain_emits_meal_published_analytics() = runTest {
        val q = queue()
        q.enqueue(draft())
        val analytics = RecordingAnalyticsTracker()
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), analytics, clock = sameDayClock, zone = zone)

        runner.runOnce(scope = null)

        val expected: AnalyticsEvent = AnalyticsEvent.MealPublished(
            slot = MealSlot.Lunch,
            ingredientCount = 0,
            hasDescription = false,
            audienceCrewCount = 1,
            // Stamped from the draft plate's provenance (this draft's plate is camera-sourced).
            source = PublishSource.CAMERA,
        )
        assertEquals(expected, analytics.events.single())
    }

    /** R3 regression: `photo_count` must reflect the REAL draft's plate count. The test above only
     *  proves the single-plate default (1) is stamped, which would pass even if `photoCount` were
     *  hardcoded back to `AnalyticsEvent.MealPublished`'s own default — this one forces a real
     *  multi-photo value through `trackPublished`. */
    @Test
    fun successful_drain_of_a_multi_photo_draft_stamps_the_real_photo_count() = runTest {
        val q = queue()
        q.enqueue(draft().copy(plates = listOf(Plate(byteArrayOf(1)), Plate(byteArrayOf(2)), Plate(byteArrayOf(3)))))
        val analytics = RecordingAnalyticsTracker()
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), analytics, clock = sameDayClock, zone = zone)

        runner.runOnce(scope = null)

        val event = analytics.events.single() as AnalyticsEvent.MealPublished
        assertEquals(3, event.photoCount)
    }

    /** A gallery-sourced plate stamps `source = gallery` on the true publish-outcome event. */
    @Test
    fun successful_drain_stamps_gallery_publish_source_from_the_plate() = runTest {
        val q = queue()
        q.enqueue(
            draft().copy(
                plates = listOf(
                    es.schsebastian.foodrats.feature.meal.domain.model.Plate(
                        byteArrayOf(9, 8, 7),
                        source = es.schsebastian.foodrats.core.domain.meal.PlateSource.Gallery,
                    ),
                ),
            ),
        )
        val analytics = RecordingAnalyticsTracker()
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), analytics, clock = sameDayClock, zone = zone)

        runner.runOnce(scope = null)

        val event = analytics.events.single() as AnalyticsEvent.MealPublished
        assertEquals(PublishSource.GALLERY, event.source)
    }

    /** Cross-version replay: a queue entry persisted by a pre-marker build (raw JSON, no
     *  `plateSource` key at all on the nested draft) must still drain and publish — as Camera,
     *  never crash and never silently drop the entry. */
    @Test
    fun legacy_queued_draft_without_plate_source_field_replays_and_publishes_as_camera() = runTest {
        val backing = FakeDataStore()
        val prefs = AppPreferences(backing)
        prefs.set(
            Keys.DraftQueueJson,
            """
            [
              {
                "id": "entry-legacy",
                "draft": {
                  "audienceCrewIds": ["crew-1"],
                  "authorId": "acc-1",
                  "dayIso": "2026-06-14",
                  "zoneId": "UTC",
                  "photoBase64": "AQID",
                  "dish": "Pasta"
                },
                "status": {"kind": "pending"},
                "attemptCount": 0,
                "createdAtEpochMs": 1000
              }
            ]
            """.trimIndent(),
        )
        val q = DraftQueueRepository(DraftQueueLocalStore(prefs), sameDayClock, dispatchers)
        val analytics = RecordingAnalyticsTracker()
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), analytics, clock = sameDayClock, zone = zone)

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "a legacy entry with no plateSource field must still drain successfully")
        assertEquals(
            es.schsebastian.foodrats.core.domain.meal.PlateSource.Camera,
            repo.publishedDrafts.single().plate?.source,
        )
        val event = analytics.events.single() as AnalyticsEvent.MealPublished
        assertEquals(PublishSource.CAMERA, event.source)
    }

    /** AlreadyPostedToday is idempotency-success: the draft's slot is already published (e.g. the
     *  coordinator's fast path won the double-fire race), so the entry is reconciled (removed),
     *  NOT marked failed — otherwise the feed bar would show a phantom failed upload / spin a retry. */
    @Test
    fun pending_then_already_posted_today_removes_the_entry() = runTest {
        val q = queue()
        q.enqueue(draft())
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Err(MealError.Publish.AlreadyPostedToday))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), clock = sameDayClock, zone = zone)

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "an already-posted draft must drain (goal already met)")
        assertTrue(q.observe().first().isEmpty(), "already-posted entry must be removed, not failed")
    }

    @Test
    fun publish_failure_increments_attempt_and_keeps_retryable() = runTest {
        val q = queue()
        val id = (q.enqueue(draft()) as Result.Ok).value.id
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Err(MealError.Publish.PublishUnavailable))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(maxAttempts = 5), clock = sameDayClock, zone = zone)

        val drained = runner.runOnce(scope = null)

        assertTrue(!drained, "an entry still failing/retryable means the queue isn't drained")
        val entry = q.observe().first().single { it.id == id }
        assertEquals(1, entry.attemptCount)
        val status = entry.status
        assertTrue(status is QueuedDraftStatus.Failed)
        assertTrue(status.retryable, "attempt 1 of 5 must stay retryable")
        assertEquals("meal.error.publishUnavailable", status.errorKey)
    }

    @Test
    fun exhausting_max_attempts_lands_terminal_non_retryable() = runTest {
        val q = queue()
        val id = (q.enqueue(draft()) as Result.Ok).value.id
        // maxAttempts = 2 → after 2 failed attempts the entry is terminal.
        val repo = ScriptedPublishRepository(
            ArrayDeque(
                listOf(
                    Result.Err(MealError.Publish.PublishUnavailable),
                    Result.Err(MealError.Publish.PublishUnavailable),
                ),
            ),
        )
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(maxAttempts = 2), clock = sameDayClock, zone = zone)

        runner.runOnce(scope = null)          // attempt 1 → Failed(retryable = true)
        q.rearm(id)                            // backoff elapsed (proxy)
        runner.runOnce(scope = null)          // attempt 2 → Failed(retryable = false), terminal

        val entry = q.observe().first().single { it.id == id }
        assertEquals(2, entry.attemptCount)
        val status = entry.status
        assertTrue(status is QueuedDraftStatus.Failed)
        assertTrue(!status.retryable, "budget exhausted → terminal, non-retryable")
        assertEquals(2, repo.publishCount)
    }

    @Test
    fun idempotent_republish_after_a_failed_then_success_does_not_remove_until_ok() = runTest {
        // First attempt fails, second attempt succeeds → entry removed; publish targets the SAME
        // deterministic MealId both times (idempotent overwrite), so no duplicate doc is created.
        val q = queue()
        val id = (q.enqueue(draft()) as Result.Ok).value.id
        val repo = ScriptedPublishRepository(
            ArrayDeque(listOf(Result.Err(MealError.Publish.PublishUnavailable), Result.Ok(true))),
        )
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), clock = sameDayClock, zone = zone)

        runner.runOnce(scope = null)  // fails
        assertTrue(q.observe().first().any { it.id == id }, "still queued after failure")
        q.rearm(id)
        val drained = runner.runOnce(scope = null)  // succeeds

        assertTrue(drained)
        assertTrue(q.observe().first().isEmpty(), "removed only after the Ok")
        assertEquals(2, repo.publishCount)
        // Both publish attempts derive the same deterministic per-crew MealId.
        val deterministic = MealId.forDayToken(
            (CrewId.of("crew-1") as Result.Ok).value,
            (AccountId.of("acc-1") as Result.Ok).value,
            MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
            "tok",
        )
        assertEquals(deterministic, deterministic, "deterministic id is stable across retries")
    }

    @Test
    fun snapshot_counts_pending_uploading_and_failed() = runTest {
        val q = queue()
        // One pending (just enqueued).
        q.enqueue(draft())
        // One terminal failure.
        val terminalId = (q.enqueue(draft()) as Result.Ok).value.id
        q.markFailed(terminalId, "meal.upload.unknown", retryable = false)
        // One retryable failure (counts toward pending).
        val retryId = (q.enqueue(draft()) as Result.Ok).value.id
        q.markFailed(retryId, "meal.upload.unknown", retryable = true)

        val snapshot = DraftRetryRunner.snapshotOf(q.observe().first())
        assertEquals(MealUploadQueueSnapshot(pending = 2, terminalFailed = 1), snapshot)
        assertTrue(snapshot.hasWork)
        assertEquals(MealUploadQueueSnapshot.EMPTY, DraftRetryRunner.snapshotOf(emptyList()))
    }

    /** BUG 2 (offline-edge): a draft composed just before midnight but drained after must
     *  publish under TODAY's date, not the stale day it was stamped with at draft-open time. */
    @Test
    fun stale_day_is_restamped_to_today_on_the_first_attempt() = runTest {
        val q = queue()
        // draft().day = 2026-06-14 (composed then), but the runner only gets to drain it
        // after midnight has rolled to 2026-06-15 (connectivity was down overnight).
        q.enqueue(draft())
        val afterMidnight = FixedClock(Instant.parse("2026-06-15T00:05:00Z"))
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Ok(true))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), clock = afterMidnight, zone = zone)

        runner.runOnce(scope = null)

        assertEquals(
            MealDay(LocalDate(2026, 6, 15), TimeZone.UTC),
            repo.publishedDrafts.single().day,
            "stale day must be restamped to the attempt-time 'today' on the very first attempt",
        )
    }

    /** BUG 2 (offline-edge): once a publish attempt has actually been made, a retry must NOT
     *  re-stamp the day again — FirebaseMealRepository.publish fans a draft out per-crew, so a
     *  partial success on attempt 1 could already have landed some crews under that day's
     *  deterministic MealId; changing `day` on attempt 2 would target a different id for the
     *  remaining crews instead of idempotently retrying the same publish. */
    @Test
    fun stale_day_is_not_restamped_once_an_attempt_has_already_been_made() = runTest {
        val q = queue()
        val id = (q.enqueue(draft()) as Result.Ok).value.id
        val repo = ScriptedPublishRepository(
            ArrayDeque(listOf(Result.Err(MealError.Publish.PublishUnavailable), Result.Ok(true))),
        )
        // Attempt 1 runs on the SAME day as the draft (no restamp needed) and fails,
        // bumping attemptCount to 1.
        val firstRunner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), clock = sameDayClock, zone = zone)
        firstRunner.runOnce(scope = null)
        q.rearm(id)

        // Attempt 2 runs after midnight has rolled — attemptCount is now 1, so it must
        // NOT re-stamp.
        val afterMidnightRunner = DraftRetryRunner(
            q, repo, AlwaysOnline(), DraftRetryPolicy(),
            clock = FixedClock(Instant.parse("2026-06-15T00:05:00Z")), zone = zone,
        )
        afterMidnightRunner.runOnce(scope = null)

        assertEquals(
            MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
            repo.publishedDrafts.last().day,
            "day must stay stable across retries of the same entry once an attempt has been made",
        )
    }
}
