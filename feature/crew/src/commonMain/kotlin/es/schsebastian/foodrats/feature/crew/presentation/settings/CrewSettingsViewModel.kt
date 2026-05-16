package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CrewSettingsViewModel(
    private val crewId: CrewId,
    private val observeCrew: ObserveCrewUseCase,
    private val leaveCrew: LeaveCrewUseCase,
    private val session: SessionProvider,
) : MviViewModel<CrewSettingsState, CrewSettingsIntent, CrewSettingsEffect>(CrewSettingsState()) {

    init {
        viewModelScope.launch {
            observeCrew(crewId).collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(crew = r.value, error = null) }
                    is Result.Err -> update { it.copy(error = r.error) }
                }
            }
        }
    }

    override suspend fun handle(intent: CrewSettingsIntent) = when (intent) {
        CrewSettingsIntent.Leave        -> doLeave()
        CrewSettingsIntent.DismissError -> update { it.copy(error = null) }
    }

    private suspend fun doLeave() {
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isLeaving = true, error = null) }
        when (val r = leaveCrew(crewId, account)) {
            is Result.Ok  -> { update { it.copy(isLeaving = false) }; emit(CrewSettingsEffect.Left) }
            is Result.Err -> update { it.copy(isLeaving = false, error = r.error) }
        }
    }
}
