package es.schsebastian.foodrats.feature.meal.domain.model

/**
 * Lifecycle of a [QueuedDraft] in the offline-first publish queue (roadmap §5.2).
 *
 * A sealed interface (not an enum) so a leaf can grow a payload later without a
 * breaking change — `Failed` already carries one. Leaves with no payload are
 * `data object`; leaves with state are `data class`.
 *
 * Transitions (driven by the data layer's retry runner; the pure
 * [es.schsebastian.foodrats.feature.meal.domain.queue.DraftRetryPolicy] decides
 * whether [Failed] is retryable or terminal):
 *
 * ```
 * Pending ──(connectivity / worker picks it up)──▶ Uploading
 * Uploading ──(publish Ok)──────────────────────▶ Succeeded   (terminal; entry is then dequeued)
 * Uploading ──(publish Err, attempts < max)─────▶ Failed(retryable = true)  ──▶ Pending (after backoff)
 * Uploading ──(publish Err, attempts ≥ max)─────▶ Failed(retryable = false) (terminal; surfaced to the user)
 * ```
 */
sealed interface QueuedDraftStatus {
    /** Enqueued and waiting for a publish attempt (no upload in flight yet). */
    data object Pending : QueuedDraftStatus

    /** A publish attempt is currently in flight. */
    data object Uploading : QueuedDraftStatus

    /** The publish succeeded. Terminal — the entry should be dequeued. */
    data object Succeeded : QueuedDraftStatus

    /**
     * The most recent publish attempt failed.
     *
     * @property errorKey opaque token the presentation layer maps to a
     *   `MealStringKey` (domain doesn't know about i18n — mirrors
     *   `MealUploadStatus.Failed.errorKey`).
     * @property retryable `true` while the attempt budget is not exhausted (the
     *   runner will back off and try again, returning the entry to [Pending]);
     *   `false` once [DraftRetryPolicy] reports the give-up terminal, at which
     *   point this is a terminal failure surfaced to the user.
     */
    data class Failed(
        val errorKey: String,
        val retryable: Boolean,
    ) : QueuedDraftStatus
}
