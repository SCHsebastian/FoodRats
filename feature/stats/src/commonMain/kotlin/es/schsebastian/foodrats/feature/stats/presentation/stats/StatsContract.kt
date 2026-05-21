package es.schsebastian.foodrats.feature.stats.presentation.stats

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot
import es.schsebastian.foodrats.feature.stats.domain.model.Tab

data class StatsState(
    val selectedTab: Tab = Tab.Week,
    val snapshot: StatsSnapshot? = null,
    val historicLoading: Boolean = false,
    val historicError: StatsError? = null,
    val error: StatsError? = null,
    val isRefreshing: Boolean = false,
    val epoch: Int = 0,
) : MviState

sealed interface StatsIntent : MviIntent {
    data class SelectTab(val tab: Tab) : StatsIntent
    data object Refresh : StatsIntent
    data object DismissError : StatsIntent
}

sealed interface StatsEffect : MviEffect
