package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.session.SignOutPort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameMemberUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CrewSettingsViewModel(
    private val crewId: CrewId,
    private val observeCrew: ObserveCrewUseCase,
    private val renameCrew: RenameCrewUseCase,
    private val renameMember: RenameMemberUseCase,
    private val deleteCrew: DeleteCrewUseCase,
    private val leaveCrew: LeaveCrewUseCase,
    private val session: SessionProvider,
    private val signOutPort: SignOutPort,
) : MviViewModel<CrewSettingsState, CrewSettingsIntent, CrewSettingsEffect>(CrewSettingsState()) {

    init {
        viewModelScope.launch {
            observeCrew(crewId).collect { r ->
                when (r) {
                    is Result.Ok -> {
                        val crew = r.value
                        val myAccountId = session.current.first()?.accountId
                        val myMember = crew.members.firstOrNull { it.accountId == myAccountId }
                        update {
                            it.copy(
                                crew = crew,
                                isOwner = crew.ownerId == myAccountId,
                                editingCrewName = if (it.editingCrewName.isEmpty()) crew.name else it.editingCrewName,
                                editingMyDisplayName = if (it.editingMyDisplayName.isEmpty()) myMember?.displayName.orEmpty() else it.editingMyDisplayName,
                                error = null,
                            )
                        }
                    }
                    is Result.Err -> update { it.copy(error = r.error) }
                }
            }
        }
    }

    override suspend fun handle(intent: CrewSettingsIntent) = when (intent) {
        is CrewSettingsIntent.CrewNameChanged -> update { it.copy(editingCrewName = intent.value) }
        CrewSettingsIntent.SaveCrewName -> doSaveCrewName()
        is CrewSettingsIntent.MyDisplayNameChanged -> update { it.copy(editingMyDisplayName = intent.value) }
        CrewSettingsIntent.SaveMyDisplayName -> doSaveMyDisplayName()
        CrewSettingsIntent.SwitchCrew -> emit(CrewSettingsEffect.NavigateToCrewPicker)
        CrewSettingsIntent.Leave -> doLeave()
        CrewSettingsIntent.SignOut -> doSignOut()
        CrewSettingsIntent.RequestDelete -> update { it.copy(showDeleteConfirm = true) }
        CrewSettingsIntent.CancelDelete -> update { it.copy(showDeleteConfirm = false) }
        CrewSettingsIntent.ConfirmDelete -> doDelete()
        CrewSettingsIntent.DismissError -> update { it.copy(error = null, signOutError = null) }
    }

    private suspend fun doSaveCrewName() {
        val name = state.value.editingCrewName
        update { it.copy(isSavingCrewName = true, error = null) }
        when (val r = renameCrew(crewId, name)) {
            is Result.Ok -> update { it.copy(isSavingCrewName = false) }
            is Result.Err -> update { it.copy(isSavingCrewName = false, error = r.error) }
        }
    }

    private suspend fun doSaveMyDisplayName() {
        val name = state.value.editingMyDisplayName
        update { it.copy(isSavingMyDisplayName = true, error = null) }
        when (val r = renameMember(name)) {
            is Result.Ok -> update { it.copy(isSavingMyDisplayName = false) }
            is Result.Err -> update { it.copy(isSavingMyDisplayName = false, error = r.error) }
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

    private suspend fun doSignOut() {
        FrLog.d(FrLog.Tags.SignOut) { "vm: doSignOut start" }
        update { it.copy(isSigningOut = true, signOutError = null) }
        when (val r = signOutPort.signOut()) {
            is Result.Ok -> {
                FrLog.d(FrLog.Tags.SignOut) { "vm: port Ok → emit SignedOut effect" }
                update { it.copy(isSigningOut = false) }
                // No explicit navigation here — the RootNavViewModel observes
                // SessionProvider.current going null and routes the app to SignIn,
                // wiping the back stack via navigateTopLevel. The effect below is
                // a hint for the screen to popBackStack defensively so the user
                // doesn't briefly see the (now stale) settings frame.
                emit(CrewSettingsEffect.SignedOut)
            }
            is Result.Err -> {
                FrLog.d(FrLog.Tags.SignOut) { "vm: port Err=${r.error}" }
                update { it.copy(isSigningOut = false, signOutError = r.error) }
            }
        }
    }
}
