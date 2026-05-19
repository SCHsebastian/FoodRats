package es.schsebastian.foodrats.feature.meal.domain.model

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.FoodTag
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

data class MealDraft(
    val crewId: CrewId,
    val authorId: AccountId,
    val day: MealDay,
    val plate: Plate?,
    val dish: DishName?,
    val tags: List<FoodTag>,
    val slot: MealSlot? = null,
)
