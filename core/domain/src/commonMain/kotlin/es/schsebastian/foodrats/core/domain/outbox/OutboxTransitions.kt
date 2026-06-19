package es.schsebastian.foodrats.core.domain.outbox

/**
 * Pure status-transition logic for an [OutboxEntry] (P2 §1 T1), the single source
 * of truth the `OutboxRunner` uses to decide what an [OutboxEntryStatus] becomes
 * after an attempt.
 *
 * Mirrors `:feature:meal`'s `DraftQueueTransitions` (kept byte-for-byte
 * untouched). Kept pure + total so every branch is unit-tested and the
 * Android/iOS runners behave identically.
 */
object OutboxTransitions {

    /** Status to set before kicking off an attempt. */
    fun beginAttempt(): OutboxEntryStatus = OutboxEntryStatus.Uploading

    /**
     * Status after a failed attempt, given the [attemptCount] *including* the
     * attempt that just failed (1-based) and the active [policy].
     *
     * - While the budget remains → [OutboxEntryStatus.Failed] with
     *   `retryable = true` (the runner backs off [OutboxRetryPolicy.nextDelay] and
     *   re-attempts; it then returns the entry to [OutboxEntryStatus.Pending]).
     * - Once the budget is exhausted → [OutboxEntryStatus.Failed] with
     *   `retryable = false` (terminal; surfaced to the user).
     *
     * @param errorKey opaque i18n token.
     */
    fun onFailure(
        attemptCount: Int,
        errorKey: String,
        policy: OutboxRetryPolicy,
    ): OutboxEntryStatus.Failed = OutboxEntryStatus.Failed(
        errorKey = errorKey,
        retryable = policy.shouldRetry(attemptCount),
    )
}
