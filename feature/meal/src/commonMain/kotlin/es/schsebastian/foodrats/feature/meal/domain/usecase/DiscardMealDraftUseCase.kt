package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository

class DiscardMealDraftUseCase(private val repository: MealRepository) {
    suspend operator fun invoke() { repository.clearDraft() }
}
