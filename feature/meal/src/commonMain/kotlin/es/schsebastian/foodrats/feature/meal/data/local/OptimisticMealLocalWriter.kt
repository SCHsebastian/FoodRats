package es.schsebastian.foodrats.feature.meal.data.local

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.OptimisticMealWritePort
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.time.Clock

/**
 * `:feature:meal`'s implementation of the cross-context [OptimisticMealWritePort] (offline-first
 * P3b §P3b-T5). Unwraps the `:core:domain` value objects and delegates to [MealLocalStore], the
 * feed's local read source-of-truth — so a feature that can't depend on this module (e.g.
 * `:feature:feed`'s `RateMealUseCase`) can still make an offline rate visible instantly.
 *
 * It carries NO IO boundary of its own — every public method delegates straight into a single
 * [MealLocalStore] write (which owns its `withContext(io)`). [Clock] stamps the optimistic rating's
 * `atMs` so it sorts consistently with server-confirmed rows.
 */
class OptimisticMealLocalWriter(
    private val local: MealLocalStore,
    private val clock: Clock,
) : OptimisticMealWritePort {

    override suspend fun applyRate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
        idempotencyKey: String,
    ) = local.applyRate(
        mealId = mealId.value,
        raterId = raterId.value,
        score = score.value,
        atMs = clock.now().toEpochMilliseconds(),
        idempotencyKey = idempotencyKey,
    )

    override suspend fun clearPending(idempotencyKey: String) = local.clearPending(idempotencyKey)
}
