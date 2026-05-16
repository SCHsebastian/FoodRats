package es.schsebastian.foodrats.feature.meal.domain.repository

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import kotlinx.coroutines.flow.Flow

interface MealRepository : MealReadPort {
    suspend fun publish(draft: MealDraft): Result<Meal, MealError>
    suspend fun delete(id: MealId): Result<Unit, MealError>
    suspend fun saveDraft(draft: MealDraft): Result<Unit, MealError>
    fun observeDraft(): Flow<MealDraft?>
    suspend fun clearDraft()
}
