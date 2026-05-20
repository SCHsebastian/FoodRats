package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class CommentText private constructor(val value: String) {
    companion object {
        const val MIN_LEN = 1
        const val MAX_LEN = 500
        fun of(raw: String): Result<CommentText, CommentValidationError> {
            val trimmed = raw.trim()
            return when {
                trimmed.length < MIN_LEN -> Result.failure(CommentValidationError.Blank)
                trimmed.length > MAX_LEN -> Result.failure(CommentValidationError.TooLong)
                else -> Result.success(CommentText(trimmed))
            }
        }
    }
}

sealed interface CommentValidationError {
    data object Blank : CommentValidationError
    data object TooLong : CommentValidationError
}
