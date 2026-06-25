package es.schsebastian.foodrats.feature.crew.presentation.invite

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.usecase.CancelJoinRequestUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.RequestToJoinCrewUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ResolveCrewByCodeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the accept-invite preview reached from a `…/invite/{code}` deep link or QR scan
 * (roadmap §3.2). On creation it resolves the crew by [code] for a rich in-app preview (name +
 * member count); on accept it FILES A JOIN REQUEST (no instant join — the owner must approve) and
 * emits [AcceptInviteEffect.RequestSent]. The typed [CrewError] tree already covers every failure
 * mode — unknown/expired code, crew gone, already a member — so no new error leaf is needed.
 */
class AcceptInviteViewModel(
    private val code: String,
    private val session: SessionProvider,
    private val resolveCrew: ResolveCrewByCodeUseCase,
    private val requestToJoin: RequestToJoinCrewUseCase,
    private val cancelJoinRequest: CancelJoinRequestUseCase,
) : MviViewModel<AcceptInviteState, AcceptInviteIntent, AcceptInviteEffect>(
    AcceptInviteState(code = code),
) {

    init {
        viewModelScope.launch { resolve() }
    }

    override suspend fun handle(intent: AcceptInviteIntent) = when (intent) {
        AcceptInviteIntent.Resolve      -> resolve()
        AcceptInviteIntent.Join         -> requestJoin()
        AcceptInviteIntent.Cancel       -> cancelRequest()
        AcceptInviteIntent.DismissError -> update { it.copy(error = null) }
    }

    private suspend fun resolve() {
        update { it.copy(isResolving = true, error = null) }
        when (val r = resolveCrew(currentState.code)) {
            is Result.Ok  -> update { it.copy(isResolving = false, crew = r.value) }
            is Result.Err -> update { it.copy(isResolving = false, error = r.error) }
        }
    }

    private suspend fun requestJoin() {
        val account = session.current.first()?.accountId
            ?: return update { it.copy(error = CrewError.Session.NotSignedIn) }
        update { it.copy(isJoining = true, error = null) }
        when (val r = requestToJoin(currentState.code, account)) {
            is Result.Ok  -> update { it.copy(isJoining = false, requestSent = true) }
            is Result.Err -> update { it.copy(isJoining = false, error = r.error) }
        }
    }

    /**
     * Withdraws the just-filed join request. Needs the resolved crew id (the request lives under
     * `crews/{crewId}/joinRequests/{me}`); if the preview never resolved there's nothing to cancel.
     * On success it returns the screen to the join CTA (`requestSent = false`).
     */
    private suspend fun cancelRequest() {
        val crewId = currentState.crew?.id ?: return
        update { it.copy(isCancelling = true, error = null) }
        when (val r = cancelJoinRequest(crewId)) {
            is Result.Ok  -> update { it.copy(isCancelling = false, requestSent = false) }
            is Result.Err -> update { it.copy(isCancelling = false, error = r.error) }
        }
    }
}
