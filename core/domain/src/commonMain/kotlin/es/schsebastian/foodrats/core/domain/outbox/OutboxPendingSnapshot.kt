package es.schsebastian.foodrats.core.domain.outbox

/**
 * Aggregate of the write outbox for the feed's pending-sync indicator (P2 §1 T8).
 *
 * Mirrors `MealUploadQueueSnapshot` for the meal-publish queue: presentation reads
 * only this small count pair, never the per-entry [OutboxEntry] list, so the UI
 * stays a pure reflection of how many mutations are queued vs. terminally failed.
 *
 * Derived from [OutboxPort.observePending] by folding [OutboxEntryStatus]:
 * - [pending] counts entries still on their way to applying ([OutboxEntryStatus.Pending],
 *   [OutboxEntryStatus.Uploading], and retryable [OutboxEntryStatus.Failed]) — these
 *   drain on their own once connectivity returns.
 * - [terminalFailed] counts entries the runner gave up on
 *   ([OutboxEntryStatus.Failed] with `retryable = false`) — they need a user
 *   retry/dismiss.
 *
 * [EMPTY] is the no-work snapshot (both counts zero); the sync bar hides itself.
 */
data class OutboxPendingSnapshot(
    val pending: Int = 0,
    val terminalFailed: Int = 0,
) {
    /** Whether anything is worth surfacing in the sync bar. */
    val hasWork: Boolean get() = pending > 0 || terminalFailed > 0

    companion object {
        val EMPTY = OutboxPendingSnapshot()

        /**
         * Fold a raw outbox listing into the count pair the UI surfaces.
         * Terminal = [OutboxEntryStatus.Failed] with `retryable = false`; everything
         * else (Pending / Uploading / retryable Failed) counts as still-pending.
         */
        fun of(entries: List<OutboxEntry>): OutboxPendingSnapshot {
            var pending = 0
            var terminal = 0
            for (entry in entries) {
                val status = entry.status
                if (status is OutboxEntryStatus.Failed && !status.retryable) {
                    terminal++
                } else {
                    pending++
                }
            }
            return OutboxPendingSnapshot(pending = pending, terminalFailed = terminal)
        }
    }
}
