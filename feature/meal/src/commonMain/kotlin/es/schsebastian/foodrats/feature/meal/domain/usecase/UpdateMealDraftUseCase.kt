package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
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
            is UpdateMealDraftCommand.AddPhoto       -> {
                if (current.plates.size >= MealPublishPolicy.MAX_PHOTOS_PER_MEAL) {
                    return Result.failure(MealError.Validation.TooManyPhotos)
                }
                current.copy(plates = current.plates + command.plate)
            }
            is UpdateMealDraftCommand.RemovePhotoAt  -> {
                if (command.index !in current.plates.indices) {
                    current
                } else {
                    current.copy(plates = current.plates.toMutableList().apply { removeAt(command.index) })
                }
            }
            is UpdateMealDraftCommand.MovePhoto      -> {
                val plates = current.plates
                if (command.fromIndex !in plates.indices || command.toIndex !in plates.indices) {
                    current
                } else {
                    current.copy(
                        plates = plates.toMutableList().apply { add(command.toIndex, removeAt(command.fromIndex)) },
                    )
                }
            }
            is UpdateMealDraftCommand.SetDish        -> current.copy(dish = command.dish)
            is UpdateMealDraftCommand.SetDescription -> current.copy(description = command.description)
            is UpdateMealDraftCommand.SetSlot        -> current.copy(slot = command.slot)
            is UpdateMealDraftCommand.SetCoordinates -> current.copy(coordinates = command.coordinates)
            is UpdateMealDraftCommand.SetAudience    -> current.copy(audienceCrewIds = command.crewIds)
            is UpdateMealDraftCommand.SetDetected    -> current.copy(
                // Detected ≠ confirmed: the classifier output seeds ONLY the detected
                // set (which the picker reads as its initial selection). The
                // user-confirmed `ingredients` list is written exclusively by
                // SetIngredients, so unattested detections never reach the published Meal.
                detectedIngredients = command.detected,
                // The detected dish slug is carried so the publish path can stamp Meal.cuisine
                // (roadmap §2.2). Like the detected ingredients it is advisory, not user-attested.
                detectedDishSlug = command.dishSlug,
                classifierVersion = command.version,
            )
            is UpdateMealDraftCommand.SetIngredients -> current.copy(ingredients = command.ingredients)
        }
        return repository.saveDraft(updated).map { updated }
    }
}
