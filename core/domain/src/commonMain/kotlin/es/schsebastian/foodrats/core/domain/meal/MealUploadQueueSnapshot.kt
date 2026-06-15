package es.schsebastian.foodrats.core.domain.meal

/**
 * Cross-feature aggregate of the offline-first publish queue (roadmap §5.2).
 *
 * The publisher feature (`:feature:meal`) owns the per-entry queue model
 * (`DraftQueuePort` / `QueuedDraft`) but must not leak it across the feature
 * boundary — `:feature:feed` cannot depend on `:feature:meal`. So the feed top
 * bar reads only this small aggregate, published through
 * [MealUploadProgressPort.queue], to render "N plates waiting to publish" and a
 * "M failed" affordance.
 *
 * - [pending] counts entries still trying to publish (queued *or* mid-upload):
 *   `Pending` + `Uploading` + retryable `Failed`. These resolve on their own
 *   once connectivity returns.
 * - [terminalFailed] counts entries the runner has given up on
 *   (`Failed(retryable = false)`) — they need a user retry/dismiss.
 *
 * [EMPTY] is the no-work snapshot (both counts zero); the top bar hides itself.
 */
data class MealUploadQueueSnapshot(
    val pending: Int = 0,
    val terminalFailed: Int = 0,
) {
    /** Whether anything is worth surfacing in the top bar. */
    val hasWork: Boolean get() = pending > 0 || terminalFailed > 0

    companion object {
        val EMPTY = MealUploadQueueSnapshot()
    }
}
