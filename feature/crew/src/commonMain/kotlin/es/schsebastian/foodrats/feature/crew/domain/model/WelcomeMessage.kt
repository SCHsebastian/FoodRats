package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.jvm.JvmInline

/**
 * A short onboarding note the crew owner pins for new joiners to read on first opening the feed
 * ("Welcome! We cook dinner together every evening — post before 22:00."). `null` in [Crew.welcomeMessage]
 * means no message is set. Capped at [MAX_LEN] characters. Managed by
 * [es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWelcomeMessageUseCase].
 */
@JvmInline
value class WelcomeMessage private constructor(val value: String) {
    companion object {
        const val MAX_LEN = 200

        /**
         * Validates and constructs a [WelcomeMessage]. Returns
         * [CrewError.Validation.WelcomeMessageTooLong] when the trimmed input exceeds [MAX_LEN].
         * An empty string returns `null` — callers interpret a blank message as "cleared".
         */
        fun of(raw: String): Result<WelcomeMessage?, CrewError.Validation> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()        -> Result.success(null)
                trimmed.length > MAX_LEN -> Result.failure(CrewError.Validation.WelcomeMessageTooLong)
                else                     -> Result.success(WelcomeMessage(trimmed))
            }
        }
    }
}
