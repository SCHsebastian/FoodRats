package es.schsebastian.foodrats.feature.stats.domain.model

import kotlinx.datetime.LocalDate

data class StatsWindow(
    val tab: Tab,
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
) {
    init { require(days >= 1) { "StatsWindow.days must be >= 1, was $days" } }
}
