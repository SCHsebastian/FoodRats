package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

/** Records optimistic-write calls so tests can assert the offline fallback rendered the star locally. */
class RecordingOptimisticMealWritePort : OptimisticMealWritePort {
    data class AppliedRate(
        val crewId: CrewId,
        val mealId: MealId,
        val raterId: AccountId,
        val score: Score,
        val idempotencyKey: String,
    )

    val applied = mutableListOf<AppliedRate>()
    val cleared = mutableListOf<String>()

    override suspend fun applyRate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
        idempotencyKey: String,
    ) {
        applied += AppliedRate(crewId, mealId, raterId, score, idempotencyKey)
    }

    override suspend fun clearPending(idempotencyKey: String) {
        cleared += idempotencyKey
    }
}
