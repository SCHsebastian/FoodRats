package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.AppSetting
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.usecase.ApproveJoinRequestUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeclineJoinRequestUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.DeleteCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.LeaveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObserveCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ObservePendingJoinRequestsUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveMemberUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RemoveCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RenameCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.TransferCrewOwnershipUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetBlindVotingUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewBannerFocalUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewBannerUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewScoreStyleUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewTaglineUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWelcomeMessageUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SetCrewWeeklyChallengeUseCase
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
    private val setCrewWelcomeMessage: SetCrewWelcomeMessageUseCase,
    private val setCrewWeeklyChallenge: SetCrewWeeklyChallengeUseCase,
    private val setCrewScoreStyle: SetCrewScoreStyleUseCase,
    private val leaveCrew: LeaveCrewUseCase,
    private val removeMember: RemoveMemberUseCase,
    private val observeJoinRequests: ObservePendingJoinRequestsUseCase,
    private val approveJoinRequest: ApproveJoinRequestUseCase,
    private val declineJoinRequest: DeclineJoinRequestUseCase,
    private val transferOwnership: TransferCrewOwnershipUseCase,
    private val setCrewBanner: SetCrewBannerUseCase,
    private val removeCrewBanner: RemoveCrewBannerUseCase,
    private val setCrewBannerFocal: SetCrewBannerFocalUseCase,
    private val welcomePort: CrewWelcomePort,
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
                                editingWelcomeMessage = if (!it.welcomeMessageSeeded) crew.welcomeMessage?.value.orEmpty()
                                else it.editingWelcomeMessage,
                                welcomeMessageSeeded = true,
                                editingWeeklyChallenge = if (!it.weeklyChallengeSeeded) crew.weeklyChallenge?.value.orEmpty()
                                else it.editingWeeklyChallenge,
                                weeklyChallengeSeeded = true,
                                error = null,
                            )
                        }
                    }
                    is Result.Err -> update { it.copy(error = r.error) }
                }
            }
        }
        viewModelScope.launch {
            // Resolve identities for BOTH current members and pending join-request authors, so the
            // owner's requests list can render the requester's name/avatar live.
            state
                .map { s ->
                    ((s.crew?.members?.map { it.accountId } ?: emptyList()) +
                        s.pendingRequests.map { it.accountId }).toSet()
                }
                .distinctUntilChanged()
                .flatMapLatest { ids -> accountRead.observeMany(ids) }
                .collect { identities -> update { it.copy(identities = identities) } }
        }
        // Pending join requests (owner-only by Firestore rule). A non-owner subscription is denied
        // → the repo emits Result.Err, which we map to an empty list (the section is owner-gated in
        // the UI anyway) rather than surfacing it as the screen's error.
        viewModelScope.launch {
            observeJoinRequests(crewId).collect { r ->
                when (r) {
                    is Result.Ok  -> update { it.copy(pendingRequests = r.value) }
                    is Result.Err -> update { it.copy(pendingRequests = emptyList()) }
                }
            }
        }
        // C9 — resolve the current banner's signed URL so the owner's reposition preview can show the
        // real image (the crew snapshot only carries the Storage path). Port resolves path → URL.
        viewModelScope.launch {
            welcomePort.observeBannerImageUrl(crewId)
                .collect { url -> update { it.copy(bannerImageUrl = url) } }
        }
    }

    override suspend fun handle(intent: CrewSettingsIntent) = when (intent) {
        is CrewSettingsIntent.CrewNameChanged -> update { it.copy(editingCrewName = intent.value) }
        CrewSettingsIntent.SaveCrewName -> doSaveCrewName()
        is CrewSettingsIntent.ToggleBlindVoting -> doToggleBlindVoting(intent.enabled)
        CrewSettingsIntent.ShareLinkTapped -> analytics.track(AnalyticsEvent.CrewInviteShared(crewId))
        CrewSettingsIntent.SwitchCrew -> emit(CrewSettingsEffect.NavigateToCrewPicker)
        CrewSettingsIntent.RequestLeave -> update { it.copy(showLeaveConfirm = true, selectedSuccessor = null) }
        CrewSettingsIntent.CancelLeave -> update { it.copy(showLeaveConfirm = false) }
        CrewSettingsIntent.ConfirmLeave -> doLeave()
        is CrewSettingsIntent.SelectSuccessor -> update { it.copy(selectedSuccessor = intent.accountId) }
        CrewSettingsIntent.RequestDelete -> update { it.copy(showDeleteConfirm = true) }
        CrewSettingsIntent.CancelDelete -> update { it.copy(showDeleteConfirm = false) }
        CrewSettingsIntent.ConfirmDelete -> doDelete()
        is CrewSettingsIntent.RemoveMemberConfirmed -> doRemoveMember(intent)
        is CrewSettingsIntent.ApproveRequest -> doApprove(intent.accountId)
        is CrewSettingsIntent.DeclineRequest -> doDecline(intent.accountId)
        is CrewSettingsIntent.RequestTransfer -> update { it.copy(transferTarget = intent.accountId) }
        CrewSettingsIntent.CancelTransfer -> update { it.copy(transferTarget = null) }
        CrewSettingsIntent.ConfirmTransfer -> doTransfer()
        CrewSettingsIntent.DismissError -> update { it.copy(error = null) }
        is CrewSettingsIntent.TaglineChanged -> update { it.copy(editingTagline = intent.value) }
        CrewSettingsIntent.SaveTagline -> doSaveTagline()
        is CrewSettingsIntent.WelcomeMessageChanged -> update { it.copy(editingWelcomeMessage = intent.value) }
        CrewSettingsIntent.SaveWelcomeMessage -> doSaveWelcomeMessage()
        is CrewSettingsIntent.WeeklyChallengeChanged -> update { it.copy(editingWeeklyChallenge = intent.value) }
        CrewSettingsIntent.SaveWeeklyChallenge -> doSaveWeeklyChallenge()
        CrewSettingsIntent.OpenScoreStylePicker -> update { it.copy(showScoreStylePicker = true) }
        CrewSettingsIntent.DismissScoreStylePicker -> update { it.copy(showScoreStylePicker = false) }
        is CrewSettingsIntent.SetScoreStyle -> doSetScoreStyle(intent.style)
        // C9 — banner
        is CrewSettingsIntent.BannerPicked -> doSetBanner(intent.bytes)
        CrewSettingsIntent.BannerPickFailed -> update { it.copy(bannerError = CrewError.Banner.PickFailed) }
        CrewSettingsIntent.RemoveBanner -> update { it.copy(showRemoveBannerConfirm = true) }
        CrewSettingsIntent.ConfirmRemoveBanner -> doRemoveBanner()
        CrewSettingsIntent.CancelRemoveBanner -> update { it.copy(showRemoveBannerConfirm = false) }
        is CrewSettingsIntent.RepositionBanner -> doSetBannerFocal(intent.focalY)
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
        val account = session.current.first()?.accountId
            ?: return update { it.copy(error = CrewError.Session.NotSignedIn) }
        // Owner leaving with members remaining → hand ownership to the chosen successor (or, when
        // none is picked, the longest-tenured member is selected by the repository). For a non-owner
        // or a sole member the successor is irrelevant.
        val successor = currentState.selectedSuccessor
        update { it.copy(isLeaving = true, showLeaveConfirm = false, error = null) }
        when (val r = leaveCrew(crewId, account, successor)) {
            is Result.Ok -> {
                analytics.track(AnalyticsEvent.CrewLeft(crewId))
                update { it.copy(isLeaving = false, selectedSuccessor = null) }
                emit(CrewSettingsEffect.Left)
            }
            is Result.Err -> update { it.copy(isLeaving = false, error = r.error) }
        }
    }

    private suspend fun doApprove(target: AccountId) {
        val name = currentState.identities[target]?.displayName
        update { it.copy(processingRequestIds = it.processingRequestIds + target, error = null) }
        when (val r = approveJoinRequest(crewId, target)) {
            is Result.Ok -> {
                update { it.copy(processingRequestIds = it.processingRequestIds - target) }
                emit(CrewSettingsEffect.MemberApproved(name))
            }
            is Result.Err -> update {
                it.copy(processingRequestIds = it.processingRequestIds - target, error = r.error)
            }
        }
    }

    private suspend fun doDecline(target: AccountId) {
        update { it.copy(processingRequestIds = it.processingRequestIds + target, error = null) }
        when (val r = declineJoinRequest(crewId, target)) {
            is Result.Ok -> {
                update { it.copy(processingRequestIds = it.processingRequestIds - target) }
                emit(CrewSettingsEffect.RequestDeclined)
            }
            is Result.Err -> update {
                it.copy(processingRequestIds = it.processingRequestIds - target, error = r.error)
            }
        }
    }

    private suspend fun doTransfer() {
        val target = currentState.transferTarget ?: return
        val name = currentState.identities[target]?.displayName
        update { it.copy(isTransferring = true, transferTarget = null, error = null) }
        when (val r = transferOwnership(crewId, target)) {
            is Result.Ok -> {
                update { it.copy(isTransferring = false) }
                emit(CrewSettingsEffect.OwnershipTransferred(name))
            }
            is Result.Err -> update { it.copy(isTransferring = false, error = r.error) }
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

    private suspend fun doSaveWelcomeMessage() {
        val message = currentState.editingWelcomeMessage
        val previousMessage = currentState.crew?.welcomeMessage?.value.orEmpty()
        update { it.copy(isSavingWelcomeMessage = true, error = null) }
        when (val r = setCrewWelcomeMessage(crewId, message)) {
            is Result.Ok  -> update { it.copy(isSavingWelcomeMessage = false) }
            is Result.Err -> update {
                // Roll back the text field to what was saved before the failed write.
                it.copy(
                    isSavingWelcomeMessage = false,
                    editingWelcomeMessage = previousMessage,
                    error = r.error,
                )
            }
        }
    }

    private suspend fun doSaveWeeklyChallenge() {
        val challenge = currentState.editingWeeklyChallenge
        val previousChallenge = currentState.crew?.weeklyChallenge?.value.orEmpty()
        update { it.copy(isSavingWeeklyChallenge = true, error = null) }
        when (val r = setCrewWeeklyChallenge(crewId, challenge)) {
            is Result.Ok  -> update { it.copy(isSavingWeeklyChallenge = false) }
            is Result.Err -> update {
                // Roll back the text field to what was saved before the failed write.
                it.copy(
                    isSavingWeeklyChallenge = false,
                    editingWeeklyChallenge = previousChallenge,
                    error = r.error,
                )
            }
        }
    }

    private suspend fun doSetScoreStyle(style: CrewScoreStyle) {
        // Optimistic: close the picker immediately. The live crew listener re-emits the actual
        // value from Firestore, so we do not need an explicit rollback — just clear the saving
        // flag and surface the error on failure.
        update { it.copy(isSavingScoreStyle = true, showScoreStylePicker = false, error = null) }
        when (val r = setCrewScoreStyle(crewId, style)) {
            is Result.Ok  -> update { it.copy(isSavingScoreStyle = false) }
            is Result.Err -> update { it.copy(isSavingScoreStyle = false, error = r.error) }
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

    // C9 — banner ─────────────────────────────────────────────────────────────────────────────────

    private suspend fun doSetBanner(bytes: ByteArray) {
        // Banner failures live in bannerError (rendered inside the banner section), NOT the shared
        // bottom `error` — that one is off-screen from the section and gets reset to null by every
        // crew listener re-emission, which used to make upload failures invisible.
        update { it.copy(isSavingBanner = true, bannerError = null) }
        when (val r = setCrewBanner(crewId, bytes)) {
            is Result.Ok  -> update { it.copy(isSavingBanner = false, bannerError = null) }
            is Result.Err -> update { it.copy(isSavingBanner = false, bannerError = r.error) }
        }
    }

    private suspend fun doRemoveBanner() {
        update { it.copy(isSavingBanner = true, showRemoveBannerConfirm = false, error = null) }
        when (val r = removeCrewBanner(crewId)) {
            is Result.Ok  -> update { it.copy(isSavingBanner = false) }
            is Result.Err -> update { it.copy(isSavingBanner = false, error = r.error) }
        }
    }

    /**
     * Persists the banner focal point after the owner drags the reposition preview. No saving
     * spinner — the drag already shows the live crop, and the crew flow re-emits the new
     * [Crew.bannerFocalY] once the write lands. Only surfaces an error if the write fails.
     */
    private suspend fun doSetBannerFocal(focalY: Float) {
        when (val r = setCrewBannerFocal(crewId, focalY)) {
            is Result.Ok  -> Unit
            is Result.Err -> update { it.copy(error = r.error) }
        }
    }
}
