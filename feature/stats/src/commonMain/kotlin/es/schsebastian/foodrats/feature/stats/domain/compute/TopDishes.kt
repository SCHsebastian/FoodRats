package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.feature.stats.domain.model.DishTally

fun computeTopDishes(meals: List<Meal>, limit: Int): List<DishTally> =
    meals.groupingBy { it.dish.value }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { DishTally(it.key, it.value) }
