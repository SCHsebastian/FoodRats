package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import kotlin.jvm.JvmInline

@JvmInline
value class CrewCode private constructor(val value: String) {
    companion object {
        const val LENGTH = 6
        /** Unambiguous alphabet: excludes 0, O, 1, I, L for human legibility. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val ALLOWED: Set<Char> = ALPHABET.toSet()

        fun of(raw: String): Result<CrewCode, CrewError.Validation> {
            val normalized = raw.trim().uppercase()
            if (normalized.length != LENGTH) return Result.failure(CrewError.Validation.CodeMalformed)
            if (normalized.any { it !in ALLOWED }) return Result.failure(CrewError.Validation.CodeMalformed)
            return Result.success(CrewCode(normalized))
        }
    }
}
