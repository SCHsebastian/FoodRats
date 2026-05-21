package es.schsebastian.foodrats.feature.stats.domain.model

data class StatsSnapshot(
    val hero: HeroStats,
    val week: WindowStats,
    val month: WindowStats,
    val historic: WindowStats?,
)
