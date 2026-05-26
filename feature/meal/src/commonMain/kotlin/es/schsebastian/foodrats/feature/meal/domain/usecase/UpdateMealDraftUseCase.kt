package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.map
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft
import es.schsebastian.foodrats.feature.meal.domain.repository.MealRepository
import kotlinx.coroutines.flow.first

class UpdateMealDraftUseCase(private val repository: MealRepository) {
    suspend operator fun invoke(command: UpdateMealDraftCommand): Result<MealDraft, MealError> {
        val current = repository.observeDraft().first()
            ?: return Result.failure(MealError.Publish.NotToday)
        val updated = when (command) {
            is UpdateMealDraftCommand.SetPhoto       -> current.copy(plate = command.plate)
            is UpdateMealDraftCommand.SetDish        -> current.copy(dish = command.dish)
            is UpdateMealDraftCommand.SetDescription -> current.copy(description = command.description)
            is UpdateMealDraftCommand.SetSlot        -> current.copy(slot = command.slot)
            is UpdateMealDraftCommand.SetCoordinates -> current.copy(coordinates = command.coordinates)
            is UpdateMealDraftCommand.SetDetected    -> current.copy(
                detectedIngredients = command.detected,
                ingredients = command.detected,
                classifierVersion = command.version,
            )
            is UpdateMealDraftCommand.SetIngredients -> current.copy(ingredients = command.ingredients)
        }
        return repository.saveDraft(updated).map { updated }
    }
}
