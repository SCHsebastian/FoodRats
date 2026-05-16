package es.schsebastian.foodrats.feature.meal.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.Score
import es.schsebastian.foodrats.feature.meal.domain.model.Plate

sealed interface UpdateMealDraftCommand {
    data class SetPhoto(val plate: Plate) : UpdateMealDraftCommand
    data class SetScore(val score: Score) : UpdateMealDraftCommand
    data class SetDish(val dish: DishName) : UpdateMealDraftCommand
    data class SetTags(val tags: List<FoodTag>) : UpdateMealDraftCommand
}
