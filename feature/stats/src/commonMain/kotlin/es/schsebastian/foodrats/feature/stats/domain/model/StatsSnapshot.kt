package es.schsebastian.foodrats.feature.stats.domain.model

data class StatsSnapshot(
    val crewStreak: Streak,
    val personalStreak: Streak,
    val topDishes: List<DishTally>,
    val leaderboard: Leaderboard,
    val tagVarietyCount: Int,
    val mealsConsidered: Int,
)
