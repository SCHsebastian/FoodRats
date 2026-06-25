package es.schsebastian.foodrats.core.domain.outbox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pure retry/backoff policy for the write outbox (P2 §1 T1).
 *
 * A verbatim COPY of `:feature:meal`'s `DraftRetryPolicy` promoted into
 * `:core:domain` so `:core:data`'s `OutboxRunner` can use it without depending on
 * a feature module. The meal original is left byte-for-byte untouched.
 *
 * Deterministic and side-effect-free so it can be unit-tested branch-by-branch
 * and shared verbatim by the Android and iOS runners. Randomised jitter (if any)
 * is the platform runner's concern — keeping this policy deterministic is what
 * makes it verifiable.
 *
 * The backoff is exponential: `initialBackoff * multiplier^(attemptCount - 1)`,
 * capped at [maxBackoff]. `attemptCount` here is "the attempt that just failed"
 * (1-based): after the 1st failure we wait [nextDelay] for `attemptCount = 1`.
 *
 * Defaults: 30s → 60s → 120s → 240s → 480s (capped at [maxBackoff] = 1h),
 * giving up after [maxAttempts] = 5 failed attempts.
 */
class OutboxRetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialBackoff: Duration = DEFAULT_INITIAL_BACKOFF,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val maxBackoff: Duration = DEFAULT_MAX_BACKOFF,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(initialBackoff > Duration.ZERO) { "initialBackoff must be positive" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(maxBackoff >= initialBackoff) { "maxBackoff must be >= initialBackoff" }
    }

    /**
     * Whether another attempt is permitted after [attemptCount] attempts have
     * already failed. `true` while the budget is not exhausted.
     *
     * @param attemptCount number of attempts that have already failed (1-based).
     */
    fun shouldRetry(attemptCount: Int): Boolean = attemptCount in 1 until maxAttempts

    /**
     * Whether the queue entry has reached the terminal give-up point: the
     * attempt budget is exhausted, so no further retry will be scheduled.
     */
    fun isExhausted(attemptCount: Int): Boolean = attemptCount >= maxAttempts

    /**
     * Delay to wait before the *next* attempt, given that [attemptCount]
     * attempts have already failed (1-based). Exponential growth capped at
     * [maxBackoff].
     *
     * Returns `null` when [shouldRetry] is `false` (budget exhausted) — there is
     * no next attempt to schedule.
     */
    fun nextDelay(attemptCount: Int): Duration? {
        if (!shouldRetry(attemptCount)) return null
        // attemptCount >= 1 here. First failure (1) waits initialBackoff.
        var delay = initialBackoff
        repeat(attemptCount - 1) {
            delay *= multiplier
            if (delay >= maxBackoff) return maxBackoff
        }
        return if (delay > maxBackoff) maxBackoff else delay
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS: Int = 5
        const val DEFAULT_MULTIPLIER: Double = 2.0
        val DEFAULT_INITIAL_BACKOFF: Duration = 30.seconds
        val DEFAULT_MAX_BACKOFF: Duration = (60 * 60).seconds // 1 hour
    }
}
