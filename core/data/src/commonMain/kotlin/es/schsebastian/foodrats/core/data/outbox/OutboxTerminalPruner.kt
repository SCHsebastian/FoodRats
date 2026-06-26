package es.schsebastian.foodrats.core.data.outbox

import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days

/**
 * Bounds outbox terminal-entry growth (offline-first M5). Terminally-failed entries that the user
 * never explicitly dismisses accumulate in the `outbox` table forever — [CachePruner] only bounds
 * the `meal` table. This pruner runs ONCE at app start on the app-lifetime [appScope] and drops
 * [OutboxEntryStatus.Failed(retryable=false)][es.schsebastian.foodrats.core.domain.outbox.OutboxEntryStatus.Failed]
 * entries older than [RETENTION_MS]. Pending, uploading, and retryable-failed entries are
 * untouched — only entries that will never drain on their own are eligible for age-out.
 *
 * Fire-and-forget (a persistence failure is logged inside the port's IO boundary, never thrown).
 * No IO boundary here: [OutboxPort.pruneTerminal]'s durable implementation owns the single
 * `withContext(dispatchers.io)`; this pruner is pure orchestration on the app scope.
 */
internal class OutboxTerminalPruner(
    private val outbox: OutboxPort,
    private val clock: Clock,
    private val appScope: CoroutineScope,
) {
    private companion object {
        /** Drop terminal entries older than 30 days. */
        val RETENTION_MS: Long = 30.days.inWholeMilliseconds
    }

    /**
     * Prune terminally-failed outbox entries whose [createdAt][es.schsebastian.foodrats.core.domain.outbox.OutboxEntry.createdAt]
     * is strictly older than `now - [RETENTION_MS]`. Fire-and-forget on [appScope]; pruning is
     * best-effort housekeeping and must not crash boot.
     */
    fun start() {
        appScope.launch {
            val nowMs = clock.now().toEpochMilliseconds()
            val cutoffMs = nowMs - RETENTION_MS
            // Fire-and-forget: the port's durable impl owns the single `withContext(dispatchers.io)`
            // (LOCAL-3 — the blocking DELETE now runs on the IO dispatcher, not Dispatchers.Default)
            // and logs any persistence failure inside that boundary. The typed Err is intentionally
            // ignored here — pruning is best-effort housekeeping and must not crash boot.
            outbox.pruneTerminal(cutoffMs)
        }
    }
}
