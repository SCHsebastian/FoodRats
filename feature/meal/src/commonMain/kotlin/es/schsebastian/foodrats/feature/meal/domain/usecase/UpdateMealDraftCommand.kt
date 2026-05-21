package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

sealed interface UpdateMealDraftCommand {
    data class SetPhoto(val plate: Plate) : UpdateMealDraftCommand
    data class SetDish(val dish: DishName) : UpdateMealDraftCommand
    data class SetDescription(val description: Description) : UpdateMealDraftCommand
    data class SetSlot(val slot: MealSlot) : UpdateMealDraftCommand
}
