package es.schsebastian.foodrats.feature.meal.domain.queue

import es.schsebastian.foodrats.feature.meal.domain.model.QueuedDraftStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftQueueTransitionsTest {

    private val policy = DraftRetryPolicy(maxAttempts = 3)

    @Test
    fun begin_attempt_is_uploading() {
        assertEquals(QueuedDraftStatus.Uploading, DraftQueueTransitions.beginAttempt())
    }

    @Test
    fun success_is_terminal_succeeded() {
        assertEquals(QueuedDraftStatus.Succeeded, DraftQueueTransitions.onSuccess())
    }

    @Test
    fun failure_within_budget_is_retryable() {
        val s = DraftQueueTransitions.onFailure(
            attemptCount = 1, errorKey = "meal.upload.unknown", policy = policy,
        )
        assertEquals("meal.upload.unknown", s.errorKey)
        assertTrue(s.retryable)

        assertTrue(DraftQueueTransitions.onFailure(2, "e", policy).retryable)
    }

    @Test
    fun failure_at_max_attempts_is_terminal() {
        val s = DraftQueueTransitions.onFailure(
            attemptCount = 3, errorKey = "meal.upload.unknown", policy = policy,
        )
        assertFalse(s.retryable)
        assertEquals("meal.upload.unknown", s.errorKey)
    }

    @Test
    fun full_lifecycle_pending_uploading_failed_then_succeeded() {
        // Pending → Uploading
        var status: QueuedDraftStatus = QueuedDraftStatus.Pending
        status = DraftQueueTransitions.beginAttempt()
        assertEquals(QueuedDraftStatus.Uploading, status)

        // Uploading → Failed(retryable) on a transient error within budget
        status = DraftQueueTransitions.onFailure(attemptCount = 1, errorKey = "e", policy = policy)
        assertTrue((status as QueuedDraftStatus.Failed).retryable)

        // …runner backs off, returns to Pending → Uploading → success
        status = DraftQueueTransitions.beginAttempt()
        status = DraftQueueTransitions.onSuccess()
        assertEquals(QueuedDraftStatus.Succeeded, status)
    }
}
