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

        fun forDaySlot(
            crewId: CrewId,
            authorId: AccountId,
            day: MealDay,
            slot: MealSlot,
        ): MealId = MealId("${crewId.value}_${authorId.value}_${day.toKey()}_${slot.key()}")
    }
}
