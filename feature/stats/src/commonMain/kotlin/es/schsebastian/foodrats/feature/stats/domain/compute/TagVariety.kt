package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Meal

fun computeTagVariety(meals: List<Meal>): Int =
    meals.flatMap { it.tags }.map { it.label.trim().lowercase() }.filter { it.isNotEmpty() }.toSet().size
