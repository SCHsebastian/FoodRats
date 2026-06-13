package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CrewSettingsViewModel(
    private val crewId: CrewId,
    private val observeCrew: ObserveCrewUseCase,
    private val renameCrew: RenameCrewUseCase,
    private val deleteCrew: DeleteCrewUseCase,
    private val leaveCrew: LeaveCrewUseCase,
    private val removeMember: RemoveMemberUseCase,
    private val session: SessionProvider,
    private val accountRead: AccountReadPort,
) : MviViewModel<CrewSettingsState, CrewSettingsIntent, CrewSettingsEffect>(CrewSettingsState()) {

    init {
        viewModelScope.launch {
            observeCrew(crewId).collect { r ->
                when (r) {
                    is Result.Ok -> {
                        val crew = r.value
                        val myAccountId = session.current.first()?.accountId
                        update {
                            it.copy(
                                crew = crew,
                                isOwner = crew.ownerId == myAccountId,
                                myAccountId = myAccountId,
                                editingCrewName = if (it.editingCrewName.isEmpty()) crew.name else it.editingCrewName,
                                error = null,
                            )
                        }
                    }
                    is Result.Err -> update { it.copy(error = r.error) }
                }
            }
        }
        viewModelScope.launch {
            state
                .map { s -> s.crew?.members?.map { it.accountId }?.toSet() ?: emptySet() }
                .distinctUntilChanged()
                .flatMapLatest { ids -> accountRead.observeMany(ids) }
                .collect { identities -> update { it.copy(identities = identities) } }
        }
    }

    override suspend fun handle(intent: CrewSettingsIntent) = when (intent) {
        is CrewSettingsIntent.CrewNameChanged -> update { it.copy(editingCrewName = intent.value) }
        CrewSettingsIntent.SaveCrewName -> doSaveCrewName()
        CrewSettingsIntent.SwitchCrew -> emit(CrewSettingsEffect.NavigateToCrewPicker)
        CrewSettingsIntent.Leave -> doLeave()
        CrewSettingsIntent.RequestDelete -> update { it.copy(showDeleteConfirm = true) }
        CrewSettingsIntent.CancelDelete -> update { it.copy(showDeleteConfirm = false) }
        CrewSettingsIntent.ConfirmDelete -> doDelete()
        is CrewSettingsIntent.RemoveMemberConfirmed -> doRemoveMember(intent)
        CrewSettingsIntent.DismissError -> update { it.copy(error = null) }
    }

    private suspend fun doSaveCrewName() {
        val name = currentState.editingCrewName
        update { it.copy(isSavingCrewName = true, error = null) }
        when (val r = renameCrew(crewId, name)) {
            is Result.Ok -> update { it.copy(isSavingCrewName = false) }
            is Result.Err -> update { it.copy(isSavingCrewName = false, error = r.error) }
        }
    }

    private suspend fun doLeave() {
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isLeaving = true, error = null) }
        when (val r = leaveCrew(crewId, account)) {
            is Result.Ok -> { update { it.copy(isLeaving = false) }; emit(CrewSettingsEffect.Left) }
            is Result.Err -> update { it.copy(isLeaving = false, error = r.error) }
        }
    }

    private suspend fun doDelete() {
        update { it.copy(isDeleting = true, showDeleteConfirm = false, error = null) }
        when (val r = deleteCrew(crewId)) {
            is Result.Ok -> { update { it.copy(isDeleting = false) }; emit(CrewSettingsEffect.Deleted) }
            is Result.Err -> update { it.copy(isDeleting = false, error = r.error) }
        }
    }

    private suspend fun doRemoveMember(intent: CrewSettingsIntent.RemoveMemberConfirmed) {
        when (val r = removeMember(intent.accountId)) {
            is Result.Ok -> Unit
            is Result.Err -> update { it.copy(error = r.error) }
        }
    }
}
