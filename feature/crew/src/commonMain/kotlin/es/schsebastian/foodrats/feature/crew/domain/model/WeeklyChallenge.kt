package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.jvm.JvmInline

/**
 * A short weekly theme/challenge the crew owner pins to the feed header — e.g. "Taco Tuesday",
 * "Soup week" — to motivate variety. No enforcement; purely motivational.
 *
 * `null` in [Crew.weeklyChallenge] means no challenge is set (or it has expired client-side).
 * Capped at [MAX_LEN] characters. Blank input clears the challenge.
 * Managed by [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWeeklyChallengeUseCase].
 */
@JvmInline
value class WeeklyChallenge private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 80

        /**
         * Validates and constructs a [WeeklyChallenge]. Returns
         * [CrewError.Validation.WeeklyChallengeTooLong] when the trimmed input exceeds [MAX_LEN].
         * An empty string returns `null` — callers interpret a blank challenge as "cleared".
         */
        fun of(raw: String): Result<WeeklyChallenge?, CrewError.Validation> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.success(null)
                trimmed.length > MAX_LEN -> Result.failure(CrewError.Validation.WeeklyChallengeTooLong)
                else                     -> Result.success(WeeklyChallenge(trimmed))
            }
        }
    }
}
