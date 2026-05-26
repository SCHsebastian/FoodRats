package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.result.Result

interface MealClassifierPort {
    suspend fun classify(jpeg: ByteArray): Result<List<DishLabel>, ClassifierError>
}
