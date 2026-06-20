package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.AppSetting
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
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
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetBlindVotingUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewTaglineUseCase
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
    private val setBlindVoting: SetBlindVotingUseCase,
    private val setCrewTagline: SetCrewTaglineUseCase,
    private val leaveCrew: LeaveCrewUseCase,
    private val removeMember: RemoveMemberUseCase,
    private val session: SessionProvider,
    private val accountRead: AccountReadPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
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
                                // Seed editingTagline from the live crew exactly once. An empty
                                // string is a legitimate edited value (owner cleared the tagline),
                                // so isEmpty() can't be the "not seeded yet" sentinel — use a flag.
                                editingTagline = if (!it.taglineSeeded) crew.tagline?.value.orEmpty()
                                else it.editingTagline,
                                taglineSeeded = true,
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
        is CrewSettingsIntent.ToggleBlindVoting -> doToggleBlindVoting(intent.enabled)
        CrewSettingsIntent.ShareLinkTapped -> analytics.track(AnalyticsEvent.CrewInviteShared(crewId))
        CrewSettingsIntent.SwitchCrew -> emit(CrewSettingsEffect.NavigateToCrewPicker)
        CrewSettingsIntent.Leave -> doLeave()
        CrewSettingsIntent.RequestDelete -> update { it.copy(showDeleteConfirm = true) }
        CrewSettingsIntent.CancelDelete -> update { it.copy(showDeleteConfirm = false) }
        CrewSettingsIntent.ConfirmDelete -> doDelete()
        is CrewSettingsIntent.RemoveMemberConfirmed -> doRemoveMember(intent)
        CrewSettingsIntent.DismissError -> update { it.copy(error = null) }
        is CrewSettingsIntent.TaglineChanged -> update { it.copy(editingTagline = intent.value) }
        CrewSettingsIntent.SaveTagline -> doSaveTagline()
    }

    private suspend fun doSaveCrewName() {
        val name = currentState.editingCrewName
        update { it.copy(isSavingCrewName = true, error = null) }
        when (val r = renameCrew(crewId, name)) {
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.CrewRenamed(crewId))
                update { it.copy(isSavingCrewName = false) }
            }
            is Result.Err -> update { it.copy(isSavingCrewName = false, error = r.error) }
        }
    }

    private suspend fun doToggleBlindVoting(enabled: Boolean) {
        update { it.copy(isSavingBlindVoting = true, error = null) }
        when (val r = setBlindVoting(crewId, enabled)) {
            // On success the crew listener re-emits with the new flag — state.crew is the
            // single source of truth for the switch's checked position; no local copy here.
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.SettingChanged(AppSetting.BLIND_VOTING, enabled = enabled))
                update { it.copy(isSavingBlindVoting = false) }
            }
            is Result.Err -> update { it.copy(isSavingBlindVoting = false, error = r.error) }
        }
    }

    private suspend fun doLeave() {
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isLeaving = true, error = null) }
        when (val r = leaveCrew(crewId, account)) {
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.CrewLeft(crewId))
                update { it.copy(isLeaving = false) }
                emit(CrewSettingsEffect.Left)
            }
            is Result.Err -> update { it.copy(isLeaving = false, error = r.error) }
        }
    }

    private suspend fun doDelete() {
        update { it.copy(isDeleting = true, showDeleteConfirm = false, error = null) }
        when (val r = deleteCrew(crewId)) {
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.CrewDeleted(crewId))
                update { it.copy(isDeleting = false) }
                emit(CrewSettingsEffect.Deleted)
            }
            is Result.Err -> update { it.copy(isDeleting = false, error = r.error) }
        }
    }

    private suspend fun doSaveTagline() {
        val tagline = currentState.editingTagline
        val previousTagline = currentState.crew?.tagline?.value.orEmpty()
        // Optimistic: show in-flight flag; no immediate state change on the crew object
        // (the live crew listener re-emits with the new tagline once the write lands).
        update { it.copy(isSavingTagline = true, error = null) }
        when (val r = setCrewTagline(crewId, tagline)) {
            is Result.Ok  -> update { it.copy(isSavingTagline = false) }
            is Result.Err -> update {
                // Roll back the text field to what was saved before the failed write.
                it.copy(isSavingTagline = false, editingTagline = previousTagline, error = r.error)
            }
        }
    }

    private suspend fun doRemoveMember(intent: CrewSettingsIntent.RemoveMemberConfirmed) {
        val target = intent.accountId
        // Capture the live name BEFORE the write, so the success snackbar can still name the member
        // after the crew flow drops their row.
        val removedName = currentState.identities[target]?.displayName
        update { it.copy(removingMemberIds = it.removingMemberIds + target, error = null) }
        when (val r = removeMember(crewId, target)) {
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.CrewMemberRemoved(crewId))
                update { it.copy(removingMemberIds = it.removingMemberIds - target) }
                emit(CrewSettingsEffect.MemberRemoved(removedName))
            }
            is Result.Err -> update {
                it.copy(removingMemberIds = it.removingMemberIds - target, error = r.error)
            }
        }
    }
}
