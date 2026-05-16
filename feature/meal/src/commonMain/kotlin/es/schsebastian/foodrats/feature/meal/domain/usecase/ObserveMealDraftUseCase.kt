package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow

class ObserveMealDraftUseCase(private val repository: MealRepository) {
    operator fun invoke(): Flow<MealDraft?> = repository.observeDraft()
}
