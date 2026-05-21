package es.schsebastian.foodrats.feature.stats.domain.model

data class HeroStats(
    val personalStreak: Streak,
    val crewStreak: Streak,
    val platesToday: Int,
    val iPostedToday: Boolean,
)
