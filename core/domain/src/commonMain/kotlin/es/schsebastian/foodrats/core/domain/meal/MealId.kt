package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.jvm.JvmInline

@JvmInline
value class MealId internal constructor(val value: String) {
    companion object {
        fun of(raw: String): Result<MealId, MealValueObjectError> {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) Result.failure(MealValueObjectError.MealIdBlank)
            else Result.success(MealId(trimmed))
        }

        /**
         * Deterministic per-(crew, author, day) meal id, keyed by an opaque [token] derived from
         * the plate content (a stable hash of the photo bytes). The token is identical across every
         * per-crew copy of one logical post, so re-publishing the same draft overwrites rather than
         * duplicates (idempotent retry) and a "delete my post" can reconstruct each crew's copy from
         * the same token. Replaces the old slot-keyed id now that slot is optional and repeatable.
         *
         * Format `crewId_authorId_dayKey_token`. None of the four parts may contain `_`
         * (Firestore auto-ids / uids are alphanumeric, `dayKey` is `YYYY-MM-DD`, the token is hex),
         * so the security rule can split on `_` to validate the shape.
         */
        fun forDayToken(
            crewId: CrewId,
            authorId: AccountId,
            day: MealDay,
            token: String,
        ): MealId = MealId("${crewId.value}_${authorId.value}_${day.toKey()}_$token")
    }
}
