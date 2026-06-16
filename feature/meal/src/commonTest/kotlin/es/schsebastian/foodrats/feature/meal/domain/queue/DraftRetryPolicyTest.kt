package es.schsebastian.foodrats.feature.meal.domain.queue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DraftRetryPolicyTest {

    private val policy = DraftRetryPolicy(
        maxAttempts = 5,
        initialBackoff = 30.seconds,
        multiplier = 2.0,
        maxBackoff = (60 * 60).seconds,
    )

    @Test
    fun first_failure_waits_initial_backoff() {
        assertEquals(30.seconds, policy.nextDelay(attemptCount = 1))
    }

    @Test
    fun backoff_grows_exponentially() {
        assertEquals(30.seconds, policy.nextDelay(1))
        assertEquals(60.seconds, policy.nextDelay(2))
        assertEquals(120.seconds, policy.nextDelay(3))
        assertEquals(240.seconds, policy.nextDelay(4))
    }

    @Test
    fun backoff_is_capped_at_max() {
        val capped = DraftRetryPolicy(
            maxAttempts = 100,
            initialBackoff = 30.seconds,
            multiplier = 2.0,
            maxBackoff = 100.seconds,
        )
        assertEquals(30.seconds, capped.nextDelay(1))
        assertEquals(60.seconds, capped.nextDelay(2))
        // 120s would exceed the 100s cap → clamped.
        assertEquals(100.seconds, capped.nextDelay(3))
        assertEquals(100.seconds, capped.nextDelay(10))
    }

    @Test
    fun should_retry_while_budget_remains() {
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(2))
        assertTrue(policy.shouldRetry(3))
        assertTrue(policy.shouldRetry(4))
    }

    @Test
    fun max_attempts_is_terminal() {
        // 5th failure exhausts the 5-attempt budget.
        assertFalse(policy.shouldRetry(5))
        assertTrue(policy.isExhausted(5))
        assertTrue(policy.isExhausted(6))
        assertNull(policy.nextDelay(5))
    }

    @Test
    fun not_exhausted_before_max() {
        assertFalse(policy.isExhausted(1))
        assertFalse(policy.isExhausted(4))
    }

    @Test
    fun single_attempt_policy_gives_up_after_first_failure() {
        val once = DraftRetryPolicy(maxAttempts = 1)
        assertFalse(once.shouldRetry(1))
        assertTrue(once.isExhausted(1))
        assertNull(once.nextDelay(1))
    }

    @Test
    fun rejects_invalid_construction() {
        assertFailsWith<IllegalArgumentException> { DraftRetryPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { DraftRetryPolicy(initialBackoff = 0.seconds) }
        assertFailsWith<IllegalArgumentException> { DraftRetryPolicy(multiplier = 0.5) }
        assertFailsWith<IllegalArgumentException> {
            DraftRetryPolicy(initialBackoff = 60.seconds, maxBackoff = 30.seconds)
        }
    }

    @Test
    fun defaults_match_documented_schedule() {
        val p = DraftRetryPolicy()
        assertEquals(5, p.maxAttempts)
        assertEquals(30.seconds, p.nextDelay(1))
        assertEquals(60.seconds, p.nextDelay(2))
        assertEquals(120.seconds, p.nextDelay(3))
        assertEquals(240.seconds, p.nextDelay(4))
        assertNull(p.nextDelay(5))
    }
}
