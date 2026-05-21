package es.schsebastian.foodrats.feature.stats.domain.model

import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealAuthor
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.meal.MealId
import kotlin.time.Instant

data class MealAward(
    val mealId: MealId,
    val photoUrl: String,
    val dish: DishName,
    val author: MealAuthor,
    val score: Double,
    val ratingCount: Int,
    val publishedAt: Instant,
    val day: MealDay,
)
