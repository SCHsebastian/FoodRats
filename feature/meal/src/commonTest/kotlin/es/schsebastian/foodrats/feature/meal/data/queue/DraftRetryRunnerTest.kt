package es.schsebastian.foodrats.feature.meal.data.queue

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

    private class AlwaysOnline : ConnectivityMonitor {
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
        plate = Plate(byteArrayOf(9, 8, 7)),
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
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy())

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
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(), analytics)

        runner.runOnce(scope = null)

        val expected: AnalyticsEvent = AnalyticsEvent.MealPublished(
            slot = MealSlot.Lunch,
            ingredientCount = 0,
            hasDescription = false,
            audienceCrewCount = 1,
            source = PublishSource.UNKNOWN,
        )
        assertEquals(expected, analytics.events.single())
    }

    /** AlreadyPostedToday is idempotency-success: the draft's slot is already published (e.g. the
     *  coordinator's fast path won the double-fire race), so the entry is reconciled (removed),
     *  NOT marked failed — otherwise the feed bar would show a phantom failed upload / spin a retry. */
    @Test
    fun pending_then_already_posted_today_removes_the_entry() = runTest {
        val q = queue()
        q.enqueue(draft())
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Err(MealError.Publish.AlreadyPostedToday))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy())

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "an already-posted draft must drain (goal already met)")
        assertTrue(q.observe().first().isEmpty(), "already-posted entry must be removed, not failed")
    }

    @Test
    fun publish_failure_increments_attempt_and_keeps_retryable() = runTest {
        val q = queue()
        val id = (q.enqueue(draft()) as Result.Ok).value.id
        val repo = ScriptedPublishRepository(ArrayDeque(listOf(Result.Err(MealError.Publish.PublishUnavailable))))
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(maxAttempts = 5))

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
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy(maxAttempts = 2))

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
        val runner = DraftRetryRunner(q, repo, AlwaysOnline(), DraftRetryPolicy())

        runner.runOnce(scope = null)  // fails
        assertTrue(q.observe().first().any { it.id == id }, "still queued after failure")
        q.rearm(id)
        val drained = runner.runOnce(scope = null)  // succeeds

        assertTrue(drained)
        assertTrue(q.observe().first().isEmpty(), "removed only after the Ok")
        assertEquals(2, repo.publishCount)
        // Both publish attempts derive the same deterministic per-crew MealId.
        val deterministic = MealId.forDaySlot(
            (CrewId.of("crew-1") as Result.Ok).value,
            (AccountId.of("acc-1") as Result.Ok).value,
            MealDay(LocalDate(2026, 6, 14), TimeZone.UTC),
            MealSlot.Lunch,
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
}
