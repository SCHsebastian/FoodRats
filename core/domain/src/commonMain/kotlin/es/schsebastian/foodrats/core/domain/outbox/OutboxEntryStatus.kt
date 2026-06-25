package es.schsebastian.foodrats.core.domain.outbox

/**
 * Lifecycle of an [OutboxEntry] in the write outbox (P2 §1 T1).
 *
 * Mirrors the meal-publish queue's `QueuedDraftStatus`: a `sealed interface`
 * (not an enum) so a leaf can grow a payload later without a breaking change —
 * [Failed] already carries one. Leaves with no payload are `data object`; leaves
 * with state are `data class`.
 *
 * Transitions (driven by the data layer's `OutboxRunner`; the pure
 * [OutboxRetryPolicy] decides whether [Failed] is retryable or terminal):
 *
 * ```
 * Pending ──(connectivity / runner picks it up)──▶ Uploading
 * Uploading ──(execute Success/AlreadyApplied)───▶ (entry removed)
 * Uploading ──(execute Retryable, attempts < max)▶ Failed(retryable = true)  ──▶ Pending (after backoff)
 * Uploading ──(execute Retryable, attempts ≥ max)▶ Failed(retryable = false) (terminal; surfaced to the user)
 * Uploading ──(execute Terminal)─────────────────▶ Failed(retryable = false) (terminal; surfaced to the user)
 * ```
 */
sealed interface OutboxEntryStatus {
    /** Enqueued and waiting for an attempt (none in flight yet). */
    data object Pending : OutboxEntryStatus

    /** An attempt is currently in flight. */
    data object Uploading : OutboxEntryStatus

    /**
     * The most recent attempt failed.
     *
     * @property errorKey opaque token the presentation layer maps to a
     *   `StringKey` (domain doesn't know about i18n).
     * @property retryable `true` while the attempt budget is not exhausted (the
     *   runner will back off and try again, returning the entry to [Pending]);
     *   `false` once [OutboxRetryPolicy] reports the give-up terminal — a terminal
     *   failure surfaced to the user.
     */
    data class Failed(
        val errorKey: String,
        val retryable: Boolean,
    ) : OutboxEntryStatus
}
