package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

/**
 * Personal tagline / bio shown under the display name on the profile and (optionally)
 * on the crew member list. Cap 100 chars. A blank bio is represented as `null` (clear) —
 * use [of] to validate before persisting.
 *
 * Lives in `:core:domain/account/` alongside [Account] and [AccountWritePort] so every
 * context that reads account data can render it without touching the data layer.
 */
@JvmInline
value class Bio internal constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 100

        /**
         * Validates and constructs a [Bio].
         *
         * - Blank (empty after trimming) → `Result.Ok(null)` (clear/remove the bio).
         * - Within cap → `Result.Ok(Bio(trimmed))`.
         * - Exceeds cap → `Result.Err(BioError.Validation.TooLong)`.
         */
        fun of(raw: String): Result<Bio?, BioError> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> Result.success(null)
                trimmed.length > MAX_LENGTH -> Result.failure(BioError.Validation.TooLong)
                else -> Result.success(Bio(trimmed))
            }
        }
    }
}

sealed interface BioError {
    sealed interface Validation : BioError {
        data object TooLong : Validation
    }
}
