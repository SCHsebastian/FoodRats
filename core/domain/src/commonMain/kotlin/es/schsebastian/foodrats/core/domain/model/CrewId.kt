package es.schsebastian.foodrats.core.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class CrewId internal constructor(val value: String) {
    companion object {
        fun of(raw: String): Result<CrewId, IdError> {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) Result.failure(IdError.Blank)
            else Result.success(CrewId(trimmed))
        }
    }
}

enum class IdError { Blank }
