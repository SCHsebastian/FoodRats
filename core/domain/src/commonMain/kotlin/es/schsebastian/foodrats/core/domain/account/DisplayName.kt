package es.schsebastian.foodrats.core.domain.account

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

/**
 * The account's canonical display name (1..40 chars after trimming). The sibling of [Bio] — the
 * one place the trim/blank/length rule lives, so the write path can't drift from itself (the cap
 * was previously duplicated across the use case and the data source).
 *
 * Unlike [Bio], a display name is required: blank is an error, not a "clear". Use [of] to validate
 * before persisting; [AccountWritePort.updateDisplayName] takes a pre-validated [DisplayName].
 *
 * Lives in `:core:domain/account/` alongside [Account] and [AccountWritePort] so any context that
 * writes account identity validates it the same way.
 */
@JvmInline
value class DisplayName internal constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 40

        /**
         * Validates and constructs a [DisplayName].
         *
         * - Blank (empty after trimming) → `Result.Err(DisplayNameError.Validation.Blank)`.
         * - Exceeds cap → `Result.Err(DisplayNameError.Validation.TooLong)`.
         * - Otherwise → `Result.Ok(DisplayName(trimmed))`.
         */
        fun of(raw: String): Result<DisplayName, DisplayNameError> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> Result.failure(DisplayNameError.Validation.Blank)
                trimmed.length > MAX_LENGTH -> Result.failure(DisplayNameError.Validation.TooLong)
                else -> Result.success(DisplayName(trimmed))
            }
        }
    }
}

sealed interface DisplayNameError {
    sealed interface Validation : DisplayNameError {
        data object Blank : Validation
        data object TooLong : Validation
    }
}
