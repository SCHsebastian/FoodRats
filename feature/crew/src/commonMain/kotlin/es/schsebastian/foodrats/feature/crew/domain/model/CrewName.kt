package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.jvm.JvmInline

@JvmInline
value class CrewName private constructor(val value: String) {
    companion object {
        const val MAX = 40

        /**
         * Single source of truth for crew-name validation, shared by create and rename.
         * Blank after trim → [CrewError.Validation.NameBlank]; over [MAX] chars →
         * [CrewError.Validation.NameTooLong]. Mirrors [CrewCode.of].
         */
        fun of(raw: String): Result<CrewName, CrewError.Validation> {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty()    -> Result.failure(CrewError.Validation.NameBlank)
                trimmed.length > MAX -> Result.failure(CrewError.Validation.NameTooLong)
                else                 -> Result.success(CrewName(trimmed))
            }
        }
    }
}
