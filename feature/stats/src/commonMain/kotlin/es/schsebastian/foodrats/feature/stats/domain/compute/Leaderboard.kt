package es.schsebastian.foodrats.feature.stats.domain.compute

import es.schsebastian.foodrats.core.domain.meal.Meal
import es.schsebastian.foodrats.feature.stats.domain.model.Leaderboard
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage

fun computeLeaderboard(meals: List<Meal>): Leaderboard {
    val grouped = meals.groupBy { it.author.accountId }
    val entries = grouped.map { (id, list) ->
        val sample = list.first()
        MemberAverage(
            accountId = id,
            displayName = sample.author.displayName,
            avatarUrl = sample.author.avatarUrl,
            averageScore = list.map { it.score.value }.average(),
            postCount = list.size,
        )
    }.sortedWith(
        compareByDescending<MemberAverage> { it.averageScore }
            .thenByDescending { it.postCount }
            .thenBy { it.displayName },
    )
    return Leaderboard(entries)
}
