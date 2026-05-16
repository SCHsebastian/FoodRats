package es.schsebastian.foodrats.feature.stats.presentation.stats

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.stats.domain.error.StatsError
import es.schsebastian.foodrats.feature.stats.domain.model.StatsSnapshot

data class StatsState(
    val snapshot: StatsSnapshot? = null,
    val isLoading: Boolean = true,
    val error: StatsError? = null,
) : MviState

sealed interface StatsIntent : MviIntent {
    data object DismissError : StatsIntent
}

sealed interface StatsEffect : MviEffect   // intentionally empty for MVP
