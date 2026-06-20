package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.jvm.JvmInline

/**
 * A short blurb the crew owner can pin to their crew: "only home-cooked", "no Scores after midnight".
 * Capped at [MAX_LEN] characters. `null` in [Crew.tagline] means no tagline is set.
 */
@JvmInline
value class CrewTagline private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 120

        /**
         * Validates and constructs a [CrewTagline]. Returns [CrewError.Validation.TaglineTooLong]
         * when the trimmed input exceeds [MAX_LEN]. An empty string returns `null` rather than
         * an error — callers interpret a blank tagline as "cleared".
         */
        fun of(raw: String): Result<CrewTagline?, CrewError.Validation> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.success(null)
                trimmed.length > MAX_LEN -> Result.failure(CrewError.Validation.TaglineTooLong)
                else                     -> Result.success(CrewTagline(trimmed))
            }
        }
    }
}
