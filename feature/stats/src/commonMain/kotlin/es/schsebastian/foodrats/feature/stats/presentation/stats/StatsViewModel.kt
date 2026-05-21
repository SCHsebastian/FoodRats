package es.schsebastian.foodrats.feature.stats.presentation.stats

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.stats.domain.model.Tab
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

class StatsViewModel(
    observeStats: ObserveStatsUseCase,
) : MviViewModel<StatsState, StatsIntent, StatsEffect>(StatsState()) {

    init {
        // Sticky historic toggle: once the user opens the Historic tab, the historic
        // observer stays subscribed for the VM lifetime so re-visits don't refetch.
        val historicEnabledFlow = state
            .map { it.selectedTab == Tab.Historic }
            .scan(false) { acc, current -> acc || current }
            .distinctUntilChanged()

        val epochFlow = state.map { it.epoch }.distinctUntilChanged()

        viewModelScope.launch {
            observeStats(historicEnabledFlow, epochFlow).collect { r ->
                when (r) {
                    is Result.Ok -> update {
                        it.copy(
                            snapshot = r.value,
                            error = null,
                            historicLoading = it.selectedTab == Tab.Historic && r.value.historic == null,
                            isRefreshing = false,
                        )
                    }
                    is Result.Err -> update { it.copy(error = r.error, isRefreshing = false) }
                }
            }
        }
    }

    override suspend fun handle(intent: StatsIntent) = when (intent) {
        is StatsIntent.SelectTab -> update {
            it.copy(
                selectedTab = intent.tab,
                historicLoading = intent.tab == Tab.Historic && it.snapshot?.historic == null,
            )
        }
        StatsIntent.Refresh      -> update { it.copy(isRefreshing = true, epoch = it.epoch + 1) }
        StatsIntent.DismissError -> update { it.copy(error = null, historicError = null) }
    }
}
