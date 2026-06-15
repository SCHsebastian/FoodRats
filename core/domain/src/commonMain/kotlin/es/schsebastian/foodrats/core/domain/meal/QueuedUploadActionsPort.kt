package es.schsebastian.foodrats.core.domain.meal

/**
 * Cross-feature write port for the offline-first publish queue's user actions
 * (roadmap §5.2). The publisher feature (`:feature:meal`) owns the per-entry
 * queue (`DraftQueuePort` / `QueuedDraft`) but must not leak it across the
 * feature boundary, so a surface like the feed top bar — which reads the
 * aggregate via [MealUploadProgressPort.queue] — drives retry/dismiss only
 * through this tiny port.
 *
 * Both actions target the **terminal** entries (`Failed(retryable = false)`):
 * the ones the retry runner has given up on and that need an explicit user
 * decision. Entries still trying on their own (pending / retryable-failed) are
 * untouched — they resolve once connectivity returns.
 *
 * The IO/choke point stays in the data layer; the UI only calls these.
 */
interface QueuedUploadActionsPort {
    /**
     * Re-arm every terminal `Failed(retryable = false)` entry back to `Pending`
     * so the retry runner drains it again. Idempotent: a re-publish overwrites
     * the deterministic `MealId` document, so it can never duplicate a meal.
     */
    suspend fun retryFailed()

    /**
     * Drop every terminal `Failed(retryable = false)` entry from the queue —
     * the user has chosen to abandon those plates.
     */
    suspend fun dismissFailed()
}
