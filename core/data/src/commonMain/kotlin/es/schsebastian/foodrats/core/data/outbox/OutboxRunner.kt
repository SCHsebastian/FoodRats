package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxCommandHandler
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntry
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryId
import es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus
import es.schsebastian.foodrats.core.domain.outbox.OutboxExecuteResult
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxRetryPolicy
import es.schsebastian.foodrats.core.domain.outbox.OutboxTransitions
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background retry runner for the write outbox (offline-first P2 §1 T3).
 *
 * The cross-feature generalization of `:feature:meal`'s `DraftRetryRunner` (kept
 * byte-for-byte untouched — the write outbox COEXISTS with the meal-publish
 * queue). Pure-Kotlin orchestration shared by both platforms: the platform pieces
 * are the durable wakeup (Android WorkManager `NetworkType.CONNECTED`; iOS next
 * foreground) and the [ConnectivityPort]. The runner itself only decides *what* to
 * replay and *when to give up*, using the pure [OutboxRetryPolicy] and
 * [OutboxTransitions].
 *
 * `:core:data` must NEVER import a `:feature:*` module, so the runner cannot
 * execute a `RateMeal` or a `RenameCrew` itself. It dispatches each
 * [OutboxEntry]'s command to the first injected [OutboxCommandHandler] whose
 * [OutboxCommandHandler.handles] returns `true` (Koin `getAll()` over the
 * feature-owned handlers).
 *
 * Drain pass ([runOnce]) — for each [OutboxEntryStatus.Pending] entry:
 *  1. [OutboxPort.markUploading] (H1: CAS Pending→Uploading; skip if not claimed),
 *  2. dispatch to the matching handler ([OutboxCommandHandler.execute]),
 *  3. [OutboxExecuteResult.Success] / [OutboxExecuteResult.AlreadyApplied] →
 *     [OutboxPort.remove] (reconcile-on-success / dedup),
 *  4. [OutboxExecuteResult.Retryable] → [OutboxPort.markFailed] with the
 *     policy-derived `retryable` (via [OutboxTransitions]); if still retryable,
 *     re-arm via one of two strategies (see [runOnce]): in-process [scheduleRetry]
 *     (delayed flip back to [OutboxEntryStatus.Pending]) or, on the worker path
 *     (`scope == null`), an immediate flip to Pending for WorkManager backoff (M2),
 *  5. [OutboxExecuteResult.Terminal] (or no handler) → [OutboxPort.markFailed]
 *     with `retryable = false` — terminal, surfaced to the user.
 * A drain pass holds a [Mutex] so connectivity + enqueue triggers can't run two
 * passes concurrently. There is exactly one runner, started once on the app scope.
 *
 * NO `withContext` here — the IO boundary lives in [OutboxRepository] (CLAUDE.md
 * rule: store none, repo one per method, runner none).
 */
class OutboxRunner(
    private val outbox: OutboxPort,
    private val handlers: List<OutboxCommandHandler>,
    private val connectivity: ConnectivityPort,
    private val policy: OutboxRetryPolicy = OutboxRetryPolicy(),
    // Reserved for future replay telemetry (mirrors DraftRetryRunner). Default Noop keeps
    // direct-construction tests green and the offline path PII-free until events are added.
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
    // Default Noop keeps direct-construction tests green; real binding injected per platform.
    private val scheduler: OutboxDrainScheduler = NoopOutboxDrainScheduler(),
) {
    private val mutex = Mutex()

    /** Outcome of a single [attempt] — used by [runOnce] for per-aggregate ordering (H2). */
    private sealed interface AttemptOutcome {
        /** Entry was successfully applied or was already applied; runner removed it. */
        data object Removed : AttemptOutcome
        /** Entry failed with a retryable error and has been re-armed for a retry. */
        data object RetryableReArmed : AttemptOutcome
        /** Entry failed with a terminal error; will not retry on its own. */
        data object Terminal : AttemptOutcome
        /**
         * The CAS claim ([OutboxPort.markUploading]) returned `false` — another drain already
         * owns this entry, or it is no longer Pending. [execute] was NOT called.
         */
        data object Skipped : AttemptOutcome
    }

    /**
     * Wire the runner's triggers onto [scope] (the app-lifetime sync scope):
     *  - drain whenever connectivity rises to online,
     *  - drain whenever a new Pending entry appears.
     * On each new-pending-entry trigger, also call [scheduler.schedule] so a durable
     * WorkManager job is enqueued: it survives process death and fires on reconnect
     * even if the app is killed before the in-process drain completes.
     * Backoff re-attempts are launched per-entry on the same [scope].
     *
     * The connectivity signal is debounced by [CONNECTIVITY_DEBOUNCE_MS] (H6): rapid network
     * transitions (WiFi→cell handover, captive-portal redirect) emit a burst of false/true toggles
     * that would otherwise fire multiple concurrent drain passes. The debounce coalesces the burst
     * into a single trigger once the link has been stable for 1 s.
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        // false→true edge of connectivity (the monitor conflates to the latest value; we drain on
        // every `true`). Debounced to coalesce rapid-toggle bursts (H6).
        connectivity.isOnline()
            .debounce(CONNECTIVITY_DEBOUNCE_MS)
            .onEach { online -> if (online) launchDrain(scope) }
            .launchIn(scope)

        // A change in the count of drainable (Pending) entries means new work to do.
        outbox.observePending()
            .map { list -> list.count { it.status is OutboxEntryStatus.Pending } }
            .distinctUntilChanged()
            .onEach { pending ->
                if (pending > 0) {
                    // In-process drain (foreground responsiveness).
                    launchDrain(scope)
                    // Durable drain (survives process death — no-op on iOS until BGTaskScheduler).
                    scheduler.schedule()
                }
            }
            .launchIn(scope)
    }

    private fun launchDrain(scope: CoroutineScope) {
        scope.launch { runOnce(scope) }
    }

    /**
     * Run a single drain pass over all currently-Pending entries. Returns `true`
     * iff the outbox holds no drainable (Pending/Uploading/retryable-Failed) work
     * afterwards — the Android worker maps `true`→success, `false`→retry.
     *
     * **Re-arm strategies for retryable failures.**
     *  - `scope != null` (in-process path): [scheduleRetry] launches a coroutine on
     *    [scope] that delays [OutboxRetryPolicy.nextDelay] then flips the entry back
     *    to [OutboxEntryStatus.Pending] — per-entry exponential backoff. The entry is
     *    left `Failed(retryable = true)` while the delay is in flight, so `runOnce`
     *    returns `false` and the next pending-count emission fires the next drain.
     *  - `scope == null` (worker path): the entry is re-armed to
     *    [OutboxEntryStatus.Pending] **immediately** (no coroutine can outlive a dying
     *    worker process), then `runOnce` returns `false` → the worker returns
     *    `Result.retry()` and WorkManager's own exponential backoff spaces the next
     *    run (M2). Without this the entry would be stuck `Failed(retryable = true)`
     *    forever on the worker path.
     *
     * **Per-aggregate ordering (H2).** Pending entries are grouped by
     * [es.schsebastian.foodrats.core.domain.outbox.PendingCommand.aggregateKey]; within each group
     * they are drained FIFO by [OutboxEntry.createdAt]. A [AttemptOutcome.RetryableReArmed] result
     * halts further processing of that group for this pass — later commands in the same group cannot
     * run before the earlier one succeeds. [AttemptOutcome.Terminal] and [AttemptOutcome.Skipped]
     * do NOT halt the group. Groups are independent: a retryable failure in one group never blocks
     * another group.
     */
    suspend fun runOnce(scope: CoroutineScope? = null): Boolean = mutex.withLock {
        val entries = outbox.observePending().first().filter { it.status is OutboxEntryStatus.Pending }

        // H2: group by aggregateKey, preserving FIFO createdAt order within each group.
        // A LinkedHashMap preserves insertion order so groups are processed in the order
        // their earliest entry was enqueued — deterministic across passes.
        val groups = LinkedHashMap<String, MutableList<OutboxEntry>>()
        for (entry in entries) {
            groups.getOrPut(entry.command.aggregateKey) { mutableListOf() }.add(entry)
        }

        for ((_, group) in groups) {
            for (entry in group) {
                val outcome = attempt(entry, scope)
                // Halt this group on a retryable failure — a later command in the same
                // aggregate cannot apply before the earlier one succeeds.
                // Terminal and Skipped do NOT halt the group.
                if (outcome is AttemptOutcome.RetryableReArmed) break
            }
        }

        // Undrained = anything still trying: Pending, mid-Uploading, or a *retryable*
        // Failed (it will be re-armed to Pending after backoff). A terminal
        // Failed(retryable = false) is "done" from the drainer's view — it won't
        // resolve on its own, so the worker shouldn't keep retrying for it.
        val remaining = outbox.observePending().first().count { e ->
            when (val s = e.status) {
                OutboxEntryStatus.Pending,
                OutboxEntryStatus.Uploading -> true
                is OutboxEntryStatus.Failed -> s.retryable
            }
        }
        remaining == 0
    }

    private suspend fun attempt(entry: OutboxEntry, scope: CoroutineScope?): AttemptOutcome {
        // H1: CAS claim — only proceed if we actually transitioned the entry from Pending.
        when (val claimed = outbox.markUploading(entry.id)) {
            is Result.Err -> {
                FrLog.w("Outbox") { "markUploading persistence error for ${entry.id.value}; skipping" }
                return AttemptOutcome.Skipped
            }
            is Result.Ok -> if (!claimed.value) {
                FrLog.d("Outbox") { "CAS miss for ${entry.id.value}; skipping (another drain owns it)" }
                return AttemptOutcome.Skipped
            }
        }

        val handler = handlers.firstOrNull { it.handles(entry.command) }
        if (handler == null) {
            // No feature handler can replay this command (e.g. a command persisted by a
            // newer build, handler module not loaded). It will never resolve on its own —
            // treat as terminal so the user can dismiss it rather than spinning forever.
            FrLog.w("Outbox") { "no handler for ${entry.command::class.simpleName}; terminal" }
            outbox.markFailed(entry.id, errorKey = "outbox.error.noHandler", retryable = false)
            return AttemptOutcome.Terminal
        }

        return when (val r = handler.execute(entry.command)) {
            // Both mean "the goal is met" — reconcile by removing. AlreadyApplied is the
            // idempotency/dedup path (e.g. the comment doc already exists, the member was
            // already removed), never a failure.
            OutboxExecuteResult.Success,
            OutboxExecuteResult.AlreadyApplied -> {
                FrLog.d("Outbox") { "replayed ${entry.command::class.simpleName} (${r::class.simpleName}); removing ${entry.id.value}" }
                outbox.remove(entry.id)
                AttemptOutcome.Removed
            }
            is OutboxExecuteResult.Retryable -> {
                val newAttemptCount = entry.attemptCount + 1
                val failed = OutboxTransitions.onFailure(newAttemptCount, r.errorKey, policy)
                outbox.markFailed(entry.id, failed.errorKey, failed.retryable)
                if (failed.retryable) {
                    val delayMs = policy.nextDelay(newAttemptCount)?.inWholeMilliseconds
                    if (scope != null && delayMs != null) {
                        // In-process path: launch a coroutine that delays then re-arms to Pending.
                        // Per-entry exponential backoff; foreground-only (coroutine lives on scope).
                        FrLog.d("Outbox") {
                            "${entry.command::class.simpleName} failed (attempt $newAttemptCount); retry in ${delayMs}ms"
                        }
                        scheduleRetry(scope, entry.id, delayMs)
                    } else {
                        // Worker path (scope == null): re-arm to Pending immediately.
                        // WorkManager's own exponential backoff spaces the next worker run, so we
                        // must NOT delay here — the worker process may die before a coroutine delay
                        // fires, which would leave the entry stuck as Failed(retryable=true) forever.
                        FrLog.d("Outbox") {
                            "${entry.command::class.simpleName} failed (attempt $newAttemptCount); re-armed to Pending for WM backoff"
                        }
                        outbox.updateStatus(entry.id, OutboxEntryStatus.Pending)
                    }
                    AttemptOutcome.RetryableReArmed
                } else {
                    FrLog.w("Outbox") { "${entry.command::class.simpleName} exhausted retries; terminal" }
                    handler.onTerminal(entry.command)
                    AttemptOutcome.Terminal
                }
            }
            // A permanent failure (auth, invalid input) — retrying cannot fix it. Land
            // terminal immediately, regardless of the attempt budget. Notify the handler.
            is OutboxExecuteResult.Terminal -> {
                FrLog.w("Outbox") { "${entry.command::class.simpleName} terminal: ${r.errorKey}" }
                outbox.markFailed(entry.id, r.errorKey, retryable = false)
                handler.onTerminal(entry.command)
                AttemptOutcome.Terminal
            }
        }
    }

    /** Flip [id] back to Pending after [delayMs] so the next drain picks it up. */
    private fun scheduleRetry(scope: CoroutineScope, id: OutboxEntryId, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            outbox.updateStatus(id, OutboxEntryStatus.Pending)
        }
    }

    private companion object {
        /** Debounce window for the connectivity signal in [start] (H6). */
        const val CONNECTIVITY_DEBOUNCE_MS = 1_000L
    }
}
