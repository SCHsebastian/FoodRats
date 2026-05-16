package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.datetime.Instant

data class Meal(
    val id: MealId,
    val author: MealAuthor,
    val crewId: CrewId,
    val day: MealDay,
    val photoUrl: String,
    val score: Score,
    val dish: DishName,
    val tags: List<FoodTag>,
    val publishedAt: Instant,
)
