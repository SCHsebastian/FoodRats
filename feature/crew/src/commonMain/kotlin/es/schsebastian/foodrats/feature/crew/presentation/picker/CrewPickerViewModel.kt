package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.usecase.CreateCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveMyCrewsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CrewPickerViewModel(
    private val session: SessionProvider,
    private val observeMyCrews: ObserveMyCrewsUseCase,
    private val createCrew: CreateCrewUseCase,
    private val joinCrew: JoinCrewByCodeUseCase,
    private val switchActive: SwitchActiveCrewUseCase,
    private val accountRead: AccountReadPort,
) : MviViewModel<CrewPickerState, CrewPickerIntent, CrewPickerEffect>(CrewPickerState()) {

    private suspend fun myDisplayName(accountId: AccountId): String =
        accountRead.observe(accountId).first()?.displayName.orEmpty()

    init {
        viewModelScope.launch {
            val account = session.current.first()?.accountId ?: return@launch
            observeMyCrews(account).collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(crews = r.value) }
                    is Result.Err -> update { it.copy(error = r.error) }
                }
            }
        }
    }

    override suspend fun handle(intent: CrewPickerIntent): Unit = when (intent) {
        CrewPickerIntent.ToggleCreateForm     -> update { it.copy(showCreateForm = !it.showCreateForm, showJoinForm = false, error = null) }
        CrewPickerIntent.ToggleJoinForm       -> update { it.copy(showJoinForm = !it.showJoinForm, showCreateForm = false, error = null) }
        is CrewPickerIntent.CreateInputChanged -> update { it.copy(createInput = intent.value) }
        is CrewPickerIntent.JoinInputChanged   -> update { it.copy(joinInput = intent.value) }
        CrewPickerIntent.SubmitCreate         -> doCreate()
        CrewPickerIntent.SubmitJoin           -> doJoin()
        is CrewPickerIntent.PickCrew          -> { switchActive(intent.crewId); emit(CrewPickerEffect.CrewSelected(intent.crewId)) }
        CrewPickerIntent.DismissError         -> update { it.copy(error = null) }
    }

    private suspend fun doCreate() {
        val state = currentState
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isCreating = true, error = null) }
        when (val r = createCrew(state.createInput, account, myDisplayName(account))) {
            is Result.Ok  -> {
                update { it.copy(isCreating = false, showCreateForm = false, createInput = "") }
                switchActive(r.value.id)
                emit(CrewPickerEffect.CrewSelected(r.value.id))
            }
            is Result.Err -> update { it.copy(isCreating = false, error = r.error) }
        }
    }

    private suspend fun doJoin() {
        val state = currentState
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isJoining = true, error = null) }
        when (val r = joinCrew(state.joinInput, account, myDisplayName(account))) {
            is Result.Ok  -> {
                update { it.copy(isJoining = false, showJoinForm = false, joinInput = "") }
                switchActive(r.value.id)
                emit(CrewPickerEffect.CrewSelected(r.value.id))
            }
            is Result.Err -> update { it.copy(isJoining = false, error = r.error) }
        }
    }
}
