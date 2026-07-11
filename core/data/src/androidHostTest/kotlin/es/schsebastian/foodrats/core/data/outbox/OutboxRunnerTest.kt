package es.schsebastian.foodrats.core.data.outbox

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.meal.CommentText
import es.schsebastian.foodrats.core.domain.meal.MealCommentId
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxError
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy
import es.schsebastian.foodrats.core.domain.outbox.PendingCommand
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxRunnerTest {

    private lateinit var db: OutboxTestDb

    @BeforeTest fun setUp() { db = OutboxTestDb() }
    @AfterTest fun tearDown() = db.close()

    private class FakeConnectivity(private val online: Boolean = true) : ConnectivityPort {
        override fun isOnline(): Flow<Boolean> = flowOf(online)
    }

    /** Connectivity backed by a [MutableStateFlow] so tests can emit rapid toggles. */
    private class FlowConnectivity : ConnectivityPort {
        val state = MutableStateFlow(false)
        override fun isOnline(): Flow<Boolean> = state.asStateFlow()
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

    private fun outbox(): OutboxRepository =
        OutboxRepository(
            db.store(),
            FixedClock(Instant.parse("2026-06-19T10:00:00Z")),
            db.dispatchers,
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
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())

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
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())

        val drained = runner.runOnce(scope = null)

        assertTrue(drained, "an already-applied command must drain (goal already met)")
        assertTrue(box.observePending().first().isEmpty(), "already-applied entry must be removed, not failed")
    }

    /**
     * Worker path (scope = null) + retryable failure: the M2 fix re-arms the entry to
     * [OutboxEntryStatus.Pending] immediately (so WorkManager's backoff drives the next
     * attempt, not an in-process delay). [runOnce] must return `false` — pending work
     * remains — and `attemptCount` must be incremented to track the retry budget.
     */
    @Test
    fun worker_path_retryable_rearmed_to_pending_and_returns_false() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Retryable("rate.offline")))
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = null)

        // M2: after worker-path retryable failure the entry is re-armed to Pending immediately.
        assertFalse(drained, "re-armed Pending entry means the outbox is not fully drained")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(1, entry.attemptCount, "attemptCount incremented so budget is consumed")
        assertEquals(OutboxEntryStatus.Pending, entry.status, "re-armed to Pending for WM backoff")
    }

    /**
     * In-process path (scope != null): a retryable failure leaves the entry as
     * [OutboxEntryStatus.Failed] with a scheduled in-coroutine backoff delay, not re-armed
     * immediately. This is the EXISTING in-process behavior — must be unchanged by M2.
     */
    @Test
    fun in_process_path_retryable_stays_failed_with_retryable_true() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Retryable("rate.offline")))
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = this) // non-null scope = in-process path

        assertFalse(drained, "a still-retryable entry means the outbox isn't drained")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(1, entry.attemptCount)
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed, "in-process path leaves as Failed pending backoff")
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
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 2))

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
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

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
        val runner = OutboxRunner(box, { listOf(unrelated) }, FakeConnectivity(), OutboxRetryPolicy())

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
        val runner = OutboxRunner(box, { listOf(refuses, accepts) }, FakeConnectivity(), OutboxRetryPolicy())

        runner.runOnce(scope = null)

        assertEquals(0, refuses.calls, "the refusing handler must not be executed")
        assertEquals(1, accepts.executeCount, "the accepting handler replays the command")
        assertTrue(box.observePending().first().isEmpty())
    }

    // ─── C1: manual retry resets attempt budget ───────────────────────────────

    /**
     * A terminal entry has `attemptCount == maxAttempts`. If [OutboxPort.requeue] is called
     * (user presses Retry) it must reset `attemptCount` to 0. On the next drain the runner
     * then sees attempt 1 of `maxAttempts` — still retryable — granting a fresh backoff
     * budget, rather than instantly landing terminal again (the bug this fixes).
     */
    @Test
    fun requeue_grants_fresh_budget_so_runner_retries_rather_than_instantly_terminal() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        // Drive to terminal: maxAttempts = 2, two Retryable results.
        val handler = ScriptedRateHandler(
            listOf(
                OutboxExecuteResult.Retryable("rate.offline"),
                OutboxExecuteResult.Retryable("rate.offline"),
            ),
        )
        val policy = OutboxRetryPolicy(maxAttempts = 2)
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), policy)

        runner.runOnce(scope = null)          // attempt 1 → Failed(retryable = true)
        box.rearm(id)                          // proxy elapsed backoff
        runner.runOnce(scope = null)          // attempt 2 → Failed(retryable = false, terminal)

        val terminal = box.observePending().first().single { it.id == id }
        assertEquals(2, terminal.attemptCount, "sanity: terminal with 2 attempts")
        assertTrue((terminal.status as OutboxEntryStatus.Failed).let { !it.retryable }, "sanity: terminal")

        // User-initiated retry via requeue (resets attemptCount = 0).
        box.requeue(id)
        val requeued = box.observePending().first().single { it.id == id }
        assertEquals(0, requeued.attemptCount, "requeue must reset attemptCount to 0")
        assertEquals(OutboxEntryStatus.Pending, requeued.status, "requeue must set status to Pending")

        // Next drain: handler gets a fresh scripted outcome (success this time).
        val finalHandler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        val runner2 = OutboxRunner(box, { listOf(finalHandler) }, FakeConnectivity(), policy)
        runner2.runOnce(scope = null)

        assertTrue(box.observePending().first().isEmpty(), "after success the entry must be removed")
        assertEquals(1, finalHandler.executeCount, "entry replayed exactly once with fresh budget")
    }

    // ─── C3: Terminal runner path calls onTerminal ─────────────────────────────

    /**
     * When the runner receives [OutboxExecuteResult.Terminal] from a handler, it must call
     * [OutboxCommandHandler.onTerminal] on that handler so side-effects (e.g. the phantom
     * optimistic star) can be rolled back. Tests both the direct-Terminal path and the
     * budget-exhausted path.
     */
    @Test
    fun terminal_execute_result_calls_on_terminal() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val onTerminalCalls = mutableListOf<PendingCommand>()
        val handler = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand) = cmd is PendingCommand.RateMeal
            override suspend fun execute(cmd: PendingCommand) =
                OutboxExecuteResult.Terminal("rate.unauthorized")
            override suspend fun onTerminal(command: PendingCommand) { onTerminalCalls += command }
        }
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())

        runner.runOnce(scope = null)

        assertEquals(1, onTerminalCalls.size, "onTerminal must fire once for a Terminal result")
        assertTrue(onTerminalCalls.single() is PendingCommand.RateMeal)
    }

    @Test
    fun budget_exhausted_calls_on_terminal() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val onTerminalCalls = mutableListOf<PendingCommand>()
        val handler = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand) = cmd is PendingCommand.RateMeal
            override suspend fun execute(cmd: PendingCommand) =
                OutboxExecuteResult.Retryable("rate.offline")
            override suspend fun onTerminal(command: PendingCommand) { onTerminalCalls += command }
        }
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 2))

        runner.runOnce(scope = null) // attempt 1 → retryable
        box.rearm(id)
        runner.runOnce(scope = null) // attempt 2 → budget exhausted → terminal

        assertEquals(1, onTerminalCalls.size, "onTerminal must fire once when budget is exhausted")
        assertTrue(onTerminalCalls.single() is PendingCommand.RateMeal)
    }

    // ─── M2: worker-path re-arm ──────────────────────────────────────────────────

    /**
     * M2 fix: when the worker path (scope = null) sees a retryable failure with budget
     * remaining, the entry must be re-armed to [OutboxEntryStatus.Pending] immediately
     * and [runOnce] must return `false`. WorkManager's own exponential backoff then
     * spaces the next worker run — there is no in-process delay.
     */
    @Test
    fun m2_worker_path_retryable_leaves_entry_pending_with_incremented_attempt() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Retryable("rate.offline")))
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = null)

        assertFalse(drained, "undrained: entry is re-armed Pending → runOnce returns false → worker retries")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(OutboxEntryStatus.Pending, entry.status, "entry re-armed to Pending for WM backoff")
        assertEquals(1, entry.attemptCount, "attemptCount incremented so budget is still consumed")
    }

    @Test
    fun m2_worker_path_terminal_after_max_attempts() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = ScriptedRateHandler(
            outcomes = listOf(
                OutboxExecuteResult.Retryable("rate.offline"),
                OutboxExecuteResult.Retryable("rate.offline"),
            ),
        )
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 2))

        // Worker wakeup 1: attempt 1 → re-armed to Pending.
        val stillWork1 = runner.runOnce(scope = null)
        assertFalse(stillWork1, "still work after attempt 1")
        assertEquals(OutboxEntryStatus.Pending, box.observePending().first().single { it.id == id }.status)

        // Worker wakeup 2: attempt 2 → budget exhausted → terminal.
        val stillWork2 = runner.runOnce(scope = null)
        assertTrue(stillWork2, "terminal entry leaves no drainable work → returns true")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(2, entry.attemptCount, "two failed attempts")
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed)
        assertFalse(status.retryable, "budget exhausted → terminal, non-retryable")
    }

    @Test
    fun observePending_reflects_a_pending_entry_then_its_drain() = runTest {
        val box = outbox()
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())

        // Enqueue first, then observe — the store flow emits the latest value on subscribe, so the
        // pending entry is the first item Turbine sees.
        box.enqueue(rateCommand())
        box.observePending().test {
            assertEquals(1, awaitItem().size, "the pending entry is observable")

            runner.runOnce(scope = null)
            // The drain may collapse the transient Uploading state; assert on the terminal emission
            // (entry removed → empty).
            assertTrue(expectMostRecentItem().isEmpty(), "drained entry is removed → observePending goes empty")

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── H1: CAS skip ──────────────────────────────────────────────────────────

    @Test
    fun h1_skips_execute_when_mark_uploading_returns_false() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))

        // A port wrapper that delegates everything to `box` except markUploading always returns false,
        // simulating a concurrent drain that already owns the entry.
        val portThatDeniesClaim = object : OutboxPort by box {
            override suspend fun markUploading(id: OutboxEntryId): Result<Boolean, OutboxError> =
                Result.Ok(false)
        }

        val runner = OutboxRunner(portThatDeniesClaim, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())
        runner.runOnce(scope = null)

        assertEquals(0, handler.executeCount, "execute must not be called when CAS claim returns false")
    }

    // ── H2: per-aggregate ordering ────────────────────────────────────────────

    @Test
    fun h2_halts_same_group_on_retryable_but_not_other_groups() = runTest {
        val box = outbox()
        val commentId = MealCommentId("c-1")
        val meal2 = (MealId.of("meal-2") as Result.Ok).value

        // PostComment and DeleteComment share aggregateKey "comment:c-1" (same commentId).
        val postCmd = PendingCommand.PostComment(
            crewId = crew,
            mealId = meal,
            commentId = commentId,
            text = CommentText.of("hello").getOrNull()!!,
            authorId = rater,
        )
        val deleteCmd = PendingCommand.DeleteComment(
            crewId = crew,
            mealId = meal,
            commentId = commentId,
        )
        // Independent command on a different meal — aggregateKey "rate:meal-2:acc-1" ≠ "comment:c-1".
        val independentCmd = PendingCommand.RateMeal(
            crewId = crew,
            mealId = meal2,
            raterId = rater,
            score = Score.of(4).getOrNull()!!,
        )

        box.enqueue(postCmd)
        box.enqueue(deleteCmd)
        box.enqueue(independentCmd)

        var postExecuted = 0
        var deleteExecuted = 0
        var independentExecuted = 0

        val handler = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand): Boolean = true
            override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult = when (cmd) {
                is PendingCommand.PostComment -> { postExecuted++; OutboxExecuteResult.Retryable("post.offline") }
                is PendingCommand.DeleteComment -> { deleteExecuted++; OutboxExecuteResult.Success }
                is PendingCommand.RateMeal -> { independentExecuted++; OutboxExecuteResult.Success }
                else -> OutboxExecuteResult.Success
            }
        }

        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))
        runner.runOnce(scope = null)

        assertEquals(1, postExecuted, "PostComment must execute once")
        assertEquals(0, deleteExecuted, "DeleteComment must NOT execute — halted by PostComment's retryable failure")
        assertEquals(1, independentExecuted, "independent RateMeal must execute despite PostComment's failure")
    }

    // ── M1: identity-preserving coalesce ──────────────────────────────────────

    // ── H6: connectivity burst debounce ───────────────────────────────────────────

    /**
     * H6: a rapid burst of online=true signals must result in only ONE drain launch via the
     * connectivity path, not one per emission — the connectivity signal is debounced by 1 s.
     *
     * We use [StandardTestDispatcher] so `delay()` inside `debounce` respects virtual time;
     * with [UnconfinedTestDispatcher] delays fire immediately and debounce has no effect.
     *
     * Measurement strategy: count how many times the outbox handler's `execute()` is called.
     * An empty outbox means no entries, so `execute` is never called. We enqueue 1 entry and
     * count handler.executeCount — with debounce(1s) + 5 true signals in 500ms, exactly 1 drain
     * fires → handler executes exactly once. Without debounce, 5 drains → execute 5 times.
     */
    @Test
    fun h6_rapid_connectivity_burst_coalesces_into_single_drain() = runTest(StandardTestDispatcher()) {
        val box = outbox()
        box.enqueue(rateCommand())

        // The handler succeeds on every call — measure how many times it executes.
        val handler = ScriptedRateHandler(List(10) { OutboxExecuteResult.Success })
        val conn = FlowConnectivity()
        val runner = OutboxRunner(box, { listOf(handler) }, conn, OutboxRetryPolicy())

        // Use backgroundScope so the long-lived launchIn coroutines don't prevent runTest from
        // completing. backgroundScope is auto-cancelled when the test body finishes.
        runner.start(backgroundScope)

        // Emit a burst of rapid true/false toggles within 500 ms (< 1 s debounce window).
        for (i in 1..5) {
            conn.state.value = (i % 2 == 1) // alternates: true, false, true, false, true
            advanceTimeBy(100)
        }
        conn.state.value = true // ensure final value is true

        // Advance past the 1 s debounce so the single debounced true fires exactly once.
        advanceTimeBy(1_500)
        advanceUntilIdle()

        // With debounce(1s), the 5-emission burst collapses to 1 drain → handler executes once.
        // Without debounce, 5 rapid true signals → 5 drains → executeCount = 5.
        assertEquals(1, handler.executeCount, "debounce must coalesce 5 true signals into 1 drain")
    }

    // ── Trigger integration: start() connectivity rising-edge ─────────────────

    /**
     * Integration test for [OutboxRunner.start]: enqueue an entry, drive [start], advance past
     * the H6 connectivity debounce (1 s), and assert the entry drained.
     *
     * Uses [StandardTestDispatcher] so `debounce()` inside [start] respects virtual time — with
     * [UnconfinedTestDispatcher] delays fire immediately and debounce collapses.
     * [backgroundScope] hosts the long-lived `launchIn` coroutines so they don't block `runTest`.
     */
    @Test
    fun start_drains_on_connectivity_rising_edge_after_debounce() = runTest(StandardTestDispatcher()) {
        val box = outbox()
        box.enqueue(rateCommand())

        val handler = ScriptedRateHandler(List(5) { OutboxExecuteResult.Success })
        val conn = FlowConnectivity()
        val runner = OutboxRunner(box, { listOf(handler) }, conn, OutboxRetryPolicy())

        // Launch start on backgroundScope so the long-lived flows don't prevent runTest from completing.
        runner.start(backgroundScope)

        // Emit connectivity = false first (no drain), then true (should trigger a drain after debounce).
        conn.state.value = false
        advanceTimeBy(100)
        conn.state.value = true
        // Not yet drained — the 1 s debounce hasn't elapsed.
        advanceTimeBy(500)
        // Advance past the debounce window so the rising-edge trigger fires.
        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(1, handler.executeCount, "start() must drain on connectivity rising edge after debounce")
        assertTrue(box.observePending().first().isEmpty(), "entry must be removed after successful drain via start()")
    }

    /**
     * Integration test for [OutboxRunner.start]: the new-pending-entry trigger fires when an entry
     * is enqueued AFTER [start] has already been called. Uses [StandardTestDispatcher].
     *
     * Strategy: start offline (connectivity=false, so connectivity trigger can't fire), enqueue an
     * entry, advance virtual time to allow the pending-count trigger and drain to complete.
     * The pending-count path has no debounce — `distinctUntilChanged()` fires on each count change
     * without a delay gate — so advancing by a small amount after enqueue is enough to tick the
     * scheduler past the `launchDrain → runOnce → handler.execute` chain.
     */
    @Test
    fun start_drains_when_new_entry_enqueued_while_running() = runTest(StandardTestDispatcher()) {
        val box = outbox()

        val handler = ScriptedRateHandler(List(5) { OutboxExecuteResult.Success })
        // Offline: connectivity trigger cannot fire (stays false throughout test).
        val conn = FlowConnectivity()
        conn.state.value = false
        val runner = OutboxRunner(box, { listOf(handler) }, conn, OutboxRetryPolicy())

        runner.start(backgroundScope)
        advanceTimeBy(100)
        advanceUntilIdle()
        assertEquals(0, handler.executeCount, "no entry yet — handler must not be called")

        // Enqueue: pending-count changes 0→1 → distinctUntilChanged passes → launchDrain fires.
        box.enqueue(rateCommand())
        // Give the pending-count trigger coroutine (on backgroundScope/StandardTestDispatcher) time to
        // process: the trigger fires via launchIn(backgroundScope), which queues the onEach block.
        // A small advanceTimeBy unblocks any waiting work; advanceUntilIdle drains the queue.
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(1, handler.executeCount, "start() must drain when a new entry is enqueued")
        assertTrue(box.observePending().first().isEmpty(), "entry must be removed after drain via pending-entry trigger")
    }

    @Test
    fun m1_reissue_preserves_id_created_at_and_attempt_count() = runTest {
        val clock = FixedClock(Instant.parse("2026-06-19T10:00:00Z"))
        val box = OutboxRepository(db.store(), clock, db.dispatchers)
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val createdAt = box.observePending().first().single().createdAt

        // Simulate one failed attempt.
        box.markUploading(id)
        box.markFailed(id, "rate.offline", retryable = true)
        val afterFail = box.observePending().first().single()
        assertEquals(1, afterFail.attemptCount, "sanity: one failed attempt recorded")

        // Re-issue the same command (same idempotency key) with a new score at a later time.
        clock.set(Instant.parse("2026-06-19T11:00:00Z"))
        box.enqueue(rateCommand(score = 5))

        val row = box.observePending().first().single()
        assertEquals(id, row.id, "M1: id must be preserved on re-issue")
        assertEquals(createdAt, row.createdAt, "M1: createdAt must be preserved on re-issue")
        assertEquals(1, row.attemptCount, "M1: attemptCount must be preserved — re-issue does not reset the retry budget")
        assertEquals(OutboxEntryStatus.Pending, row.status, "M1: status reset to Pending on re-issue")
        assertEquals(5, (row.command as PendingCommand.RateMeal).score.value, "M1: payload updated to new score")
    }

    // ── BUG FIX: orphaned Uploading entries ───────────────────────────────────

    /**
     * A row can be left durably [OutboxEntryStatus.Uploading] forever if the process (or the
     * coroutine carrying [OutboxCommandHandler.execute]) dies right after the CAS claim
     * ([OutboxPort.markUploading]) committed but before the outcome was recorded. Only
     * [OutboxEntryStatus.Pending] entries are ever picked up by a drain, so without a boot-time
     * reconciliation the entry — and the write it represents — is silently lost. [start] must
     * flip such a row back to Pending so it is retried on the very next drain.
     */
    @Test
    fun start_reconciles_a_stale_uploading_row_to_pending_and_drains_it() = runTest(StandardTestDispatcher()) {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        // Simulate a previous process dying mid-execute: the CAS claim committed the row to
        // Uploading, but no outcome (markFailed / remove) ever followed.
        box.markUploading(id)
        assertEquals(
            OutboxEntryStatus.Uploading,
            box.observePending().first().single { it.id == id }.status,
            "sanity: entry is stuck Uploading before start()",
        )

        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Success))
        // Stays offline throughout — isolates the reconciliation/pending-count trigger from the
        // connectivity trigger.
        val conn = FlowConnectivity()
        val runner = OutboxRunner(box, { listOf(handler) }, conn, OutboxRetryPolicy())

        runner.start(backgroundScope)
        advanceTimeBy(100)
        advanceUntilIdle()

        assertEquals(1, handler.executeCount, "the reconciled entry must actually be retried, not stuck forever")
        assertTrue(box.observePending().first().isEmpty(), "reconciled entry drains normally once un-stuck")
    }

    /**
     * If [OutboxCommandHandler.execute] throws an unexpected [Throwable] (a feature-handler bug,
     * not a modeled [OutboxExecuteResult]), the entry must not be left claimed
     * [OutboxEntryStatus.Uploading] forever — it must be treated exactly like a modeled
     * [OutboxExecuteResult.Retryable] and flow through the normal backoff/retry machinery.
     * Uses the in-process path (`scope != null`) so the post-attempt status is directly
     * observable as [OutboxEntryStatus.Failed]`(retryable = true)` (the worker path re-arms
     * straight to Pending instead — see `worker_path_retryable_rearmed_to_pending_and_returns_false`).
     */
    @Test
    fun handler_throwing_leaves_the_entry_retryable_not_stuck_uploading() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand): Boolean = cmd is PendingCommand.RateMeal
            override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult =
                throw IllegalStateException("boom")
        }
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy(maxAttempts = 5))

        val drained = runner.runOnce(scope = this) // in-process path — see doc above

        assertFalse(drained, "a thrown handler must not look like a fully drained outbox")
        val entry = box.observePending().first().single { it.id == id }
        assertNotEquals(
            OutboxEntryStatus.Uploading,
            entry.status,
            "must not stay claimed Uploading after a thrown execute — that's the bug this fixes",
        )
        val status = entry.status
        assertTrue(status is OutboxEntryStatus.Failed, "a thrown execute must be treated as a retryable failure")
        assertTrue(status.retryable, "budget not exhausted yet — must remain retryable")
        assertEquals(1, entry.attemptCount)
    }

    /**
     * A [CancellationException] thrown out of [OutboxCommandHandler.execute] (the coroutine
     * carrying the drain being cancelled mid-call, e.g. a lifecycle-scoped `scope` tearing down)
     * must restore the entry to Pending — never left claimed Uploading — and must propagate,
     * never be swallowed as a modeled failure.
     */
    @Test
    fun handler_cancellation_restores_pending_and_rethrows() = runTest {
        val box = outbox()
        val id = (box.enqueue(rateCommand()) as Result.Ok).value.id
        val handler = object : OutboxCommandHandler {
            override fun handles(cmd: PendingCommand): Boolean = cmd is PendingCommand.RateMeal
            override suspend fun execute(cmd: PendingCommand): OutboxExecuteResult =
                throw CancellationException("cancelled")
        }
        val runner = OutboxRunner(box, { listOf(handler) }, FakeConnectivity(), OutboxRetryPolicy())

        var rethrew = false
        try {
            runner.runOnce(scope = null)
        } catch (e: CancellationException) {
            rethrew = true
        }

        assertTrue(rethrew, "CancellationException must propagate, never be swallowed")
        val entry = box.observePending().first().single { it.id == id }
        assertEquals(
            OutboxEntryStatus.Pending,
            entry.status,
            "must be restored to Pending, not left stuck Uploading, when the drain is cancelled mid-execute",
        )
    }

    // ── BUG FIX: ignored status-write Results now reported ────────────────────

    /**
     * A failed [OutboxPort.markFailed] write used to be silently ignored, desyncing the queue
     * with nothing observable. It must now be reported via the injected [CrashReporter].
     */
    @Test
    fun failed_status_write_is_reported_to_the_crash_reporter() = runTest {
        val box = outbox()
        box.enqueue(rateCommand())
        val handler = ScriptedRateHandler(listOf(OutboxExecuteResult.Retryable("rate.offline")))
        val recordedTags = mutableListOf<String?>()
        val fakeCrashReporter = object : CrashReporter {
            override fun recordNonFatal(throwable: Throwable, tag: String?) {
                recordedTags += tag
            }
            override fun log(message: String) = Unit
        }
        // A port wrapper that fails every markFailed write, simulating a persistence hiccup right
        // after a successful CAS claim.
        val failingWritesPort = object : OutboxPort by box {
            override suspend fun markFailed(
                id: OutboxEntryId,
                errorKey: String,
                retryable: Boolean,
            ): Result<Unit, OutboxError> = Result.Err(OutboxError.PersistenceUnavailable)
        }
        val runner = OutboxRunner(
            failingWritesPort,
            { listOf(handler) },
            FakeConnectivity(),
            OutboxRetryPolicy(maxAttempts = 5),
            crashReporter = fakeCrashReporter,
        )

        runner.runOnce(scope = null)

        assertTrue(recordedTags.contains("Outbox"), "a failed status write must be reported, not silently swallowed")
    }
}
