package es.schsebastian.foodrats.feature.meal.domain.queue

import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus

/**
 * Pure status-transition logic for a queued draft (roadmap §5.2), the single
 * source of truth the data-layer retry runner uses to decide what a
 * [QueuedDraftStatus] becomes after a publish attempt. Kept pure + total so
 * every branch is unit-tested and the Android/iOS runners behave identically.
 */
object DraftQueueTransitions {

    /** Status to set before kicking off a publish attempt. */
    fun beginAttempt(): QueuedDraftStatus = QueuedDraftStatus.Uploading

    /** Status after a successful publish — terminal; the entry should be dequeued. */
    fun onSuccess(): QueuedDraftStatus = QueuedDraftStatus.Succeeded

    /**
     * Status after a failed publish, given the [attemptCount] *including* the
     * attempt that just failed (1-based) and the active [policy].
     *
     * - While the budget remains → [QueuedDraftStatus.Failed] with
     *   `retryable = true` (the runner backs off [DraftRetryPolicy.nextDelay]
     *   and re-attempts; it then returns the entry to [QueuedDraftStatus.Pending]).
     * - Once the budget is exhausted → [QueuedDraftStatus.Failed] with
     *   `retryable = false` (terminal; surfaced to the user).
     *
     * @param errorKey opaque i18n token, mirrors `MealUploadStatus.Failed.errorKey`.
     */
    fun onFailure(
        attemptCount: Int,
        errorKey: String,
        policy: DraftRetryPolicy,
    ): QueuedDraftStatus.Failed = QueuedDraftStatus.Failed(
        errorKey = errorKey,
        retryable = policy.shouldRetry(attemptCount),
    )
}
