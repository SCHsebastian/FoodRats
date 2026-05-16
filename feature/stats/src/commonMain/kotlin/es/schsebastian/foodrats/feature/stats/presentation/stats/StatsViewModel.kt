package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.launch

class StatsViewModel(
    private val observeStats: ObserveStatsUseCase,
) : MviViewModel<StatsState, StatsIntent, StatsEffect>(StatsState()) {

    init {
        viewModelScope.launch {
            observeStats().collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(isLoading = false, snapshot = r.value, error = null) }
                    is Result.Err -> update { it.copy(isLoading = false, error = r.error) }
                }
            }
        }
    }

    override suspend fun handle(intent: StatsIntent) = when (intent) {
        StatsIntent.DismissError -> update { it.copy(error = null) }
    }
}
