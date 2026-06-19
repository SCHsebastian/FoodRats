package es.schsebastian.foodrats.core.data.outbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxRunnerTest {

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

    private class FakeConnectivity(private val online: Boolean = true) : ConnectivityPort {
        override fun isOnline(): Flow<Boolean> = flowOf(online)
    }

    /** Handler that handles only [PendingCommand.RateMeal], scripting its [execute] outcomes per call. */
    private class ScriptedRateHandler(
        outcomes: List<OutboxExecuteResult>,
    ) : OutboxCommandHandler {
        private val queue = ArrayDeque(outcomes)
        var executeCount = 0
            private set
        override fun handles(cmd: PendingCommand): Boolean = cmd is PendingCommand.RateMeal
        override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult {
            executeCount++
            return queue.removeFirstOrNull() ?: OutboxExecuteResult.Retryable("rate.offline")
        }
    }

    private fun outbox(backing: DataStore<Preferences> = FakeDataStore()): OutboxRepository =
        OutboxRepository(
            OutboxLocalStore(AppPreferences(backing)),
            FixedClock(Instant.parse("2026-06-19T10:00:00Z")),
            dispatchers,
        )

    private val crew = (CrewId.of("crew-1") as Result.Ok).value
    private val meal = (MealId.of("meal-1") as Result.Ok).value
    private val rater = (AccountId.of("acc-1") as Result.Ok).value

    private fun rateCommand(score: Int = 4) = PendingCommand.RateMeal(
        crewId = crew, mealId = meal, raterId = rater, score = Score.of(score).getOrNull()!!,
    )

    /** Flip a failed/retryable entry back to Pending — proxy for the elapsed backoff / WorkManager re-fire. */
    private suspend fun OutboxRepository.rearm(id: es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId) =
        updateStatus(id, OutboxEntryStatus.Pending)

    @Test
    fun success_removes_the_entry() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy())

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "outbox should be fully drained after a successful replay")
        assertEquals(1, handler.executeCount)
        assertTrue(box.observePending().first().isEmpty(), "successful entry must be reconciled (removed)")
    }

    @Test
    fun already_applied_removes_the_entry() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.AlreadyApplied))
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy())

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "an already-applied command must drain (goal already met)")
        assertTrue(box.observePending().first().isEmpty(), "already-applied entry must be removed, not failed")
    }

    @Test
    fun retryable_increments_attempt_and_stays_retryable() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Retryable("rate.offline")))
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = null)

        assertFalse(drained, "a still-retryable entry means the outbox isn't drained")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(1, entry.attemptCount)
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed)
        assertTrue(status.retryable, "attempt 1 of 5 must stay retryable")
        assertEquals("rate.offline", status.errorKey)
    }

    @Test
    fun exhausting_max_attempts_lands_terminal_non_retryable() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(
            listOf(OutboxExecuteResult.Retryable("rate.offline"), OutboxExecuteResult.Retryable("rate.offline")),
        )
        // maxAttempts = 2 → after 2 failed attempts the entry is terminal.
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 2))

        runner.runOnce(scope = null) // attempt 1 → Failed(retryable = true)
        box.rearm(id)                 // backoff elapsed (proxy)
        runner.runOnce(scope = null) // attempt 2 → Failed(retryable = false), terminal

        val entry = box.observePending().first().single { it.id == id }
        assertEquals(2, entry.attemptCount)
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed)
        assertFalse(status.retryable, "budget exhausted → terminal, non-retryable")
        assertEquals(2, handler.executeCount)
    }

    @Test
    fun terminal_result_lands_non_retryable_immediately() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Terminal("rate.unauthorized")))
        // maxAttempts high — a Terminal result must NOT consult the budget; it's terminal at once.
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "a terminal (non-retryable) entry leaves no drainable work")
        val entry = box.observePending().first().single { it.id == id }
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed)
        assertFalse(status.retryable, "Terminal → non-retryable regardless of budget")
        assertEquals("rate.unauthorized", status.errorKey)
    }

    @Test
    fun no_matching_handler_lands_terminal() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        // A handler that handles nothing relevant (refuses RateMeal).
        val unrelated = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand): Boolean = false
            override suspend fun execute(cmd: PendingCommand) = OutboxExecuteResult.Success
        }
        val runner = OutboxRunner(box, listOf(unrelated), FakeConnectivity(), OutboxRetryPolicy())

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "an unhandleable command is terminal — no drainable work remains")
        val status = box.observePending().first().single().status
        assertTrue(status is OutboxEntryStatus.Failed)
        assertFalse(status.retryable, "no handler → terminal, non-retryable")
    }

    @Test
    fun dispatches_to_the_first_handler_that_handles() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val refuses = object : OutboxCommandHandler {
            var calls = 0
            override fun handles(cmd: PendingCommand): Boolean = false
            override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult {
                calls++; return OutboxExecuteResult.Success
            }
        }
        val accepts = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        val runner = OutboxRunner(box, listOf(refuses, accepts), FakeConnectivity(), OutboxRetryPolicy())

        runner.runOnce(scope = null)

        assertEquals(0, refuses.calls, "the refusing handler must not be executed")
        assertEquals(1, accepts.executeCount, "the accepting handler replays the command")
        assertTrue(box.observePending().first().isEmpty())
    }

    @Test
    fun observePending_reflects_a_pending_entry_then_its_drain() = runTest {
        val box = outbox()
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        val runner = OutboxRunner(box, listOf(handler), FakeConnectivity(), OutboxRetryPolicy())

        // Enqueue first, then observe — the conflated store flow emits the latest value on
        // subscribe, so the pending entry is the first item Turbine sees.
        box.enqueue(rateCommand())
        box.observePending().test {
            assertEquals(1, awaitItem().size, "the pending entry is observable")

            runner.runOnce(scope = null)
            // The drain may collapse the transient Uploading state under the conflated flow;
            // assert on the terminal emission (entry removed → empty).
            assertTrue(expectMostRecentItem().isEmpty(), "drained entry is removed → observePending goes empty")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
