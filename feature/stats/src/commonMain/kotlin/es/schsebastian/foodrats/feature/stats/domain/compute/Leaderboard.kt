package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.MealWithRatings
import es.schsebastian.foodrats.feature.stats.domain.model.Leaderboard
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage

fun computeLeaderboard(meals: List<MealWithRatings>): Leaderboard {
    val grouped = meals.groupBy { it.meal.author.accountId }
    val entries = grouped.mapNotNull { (id, list) ->
        val averages = list.mapNotNull { it.averageScore }
        if (averages.isEmpty()) null
        else {
            val sample = list.first().meal.author
            MemberAverage(
                accountId = id,
                displayName = sample.displayName,
                avatarUrl = sample.avatarUrl,
                averageScore = averages.average(),
                postCount = list.size,
            )
        }
    }.sortedWith(
        compareByDescending<MemberAverage> { it.averageScore }
            .thenByDescending { it.postCount }
            .thenBy { it.displayName },
    )
    return Leaderboard(entries)
}
