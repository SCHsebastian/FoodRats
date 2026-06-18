package es.schsebastian.foodrats.feature.crew.presentation.invite

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.JoinMethod
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.usecase.JoinCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.ResolveCrewByCodeUseCase
import es.schsebastian.foodrats.feature.crew.domain.usecase.SwitchActiveCrewUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Drives the accept-invite preview reached from a `…/invite/{code}` deep link or QR scan
 * (roadmap §3.2). On creation it resolves the crew by [code] for a rich in-app preview (name +
 * member count); on accept it runs the existing join-by-code path, switches the active crew, and
 * emits [AcceptInviteEffect.Joined]. The typed [CrewError] tree already covers every failure mode —
 * unknown/expired code, crew full, already a member — so no new error leaf is needed.
 */
class AcceptInviteViewModel(
    private val code: String,
    private val session: SessionProvider,
    private val resolveCrew: ResolveCrewByCodeUseCase,
    private val joinCrew: JoinCrewByCodeUseCase,
    private val switchActive: SwitchActiveCrewUseCase,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<AcceptInviteState, AcceptInviteIntent, AcceptInviteEffect>(
    AcceptInviteState(code = code),
) {

    init {
        viewModelScope.launch { resolve() }
    }

    override suspend fun handle(intent: AcceptInviteIntent) = when (intent) {
        AcceptInviteIntent.Resolve      -> resolve()
        AcceptInviteIntent.Join         -> join()
        AcceptInviteIntent.DismissError -> update { it.copy(error = null) }
    }

    private suspend fun resolve() {
        update { it.copy(isResolving = true, error = null) }
        when (val r = resolveCrew(currentState.code)) {
            is Result.Ok  -> update { it.copy(isResolving = false, crew = r.value) }
            is Result.Err -> update { it.copy(isResolving = false, error = r.error) }
        }
    }

    private suspend fun join() {
        val account = session.current.first()?.accountId ?: return
        update { it.copy(isJoining = true, error = null) }
        when (val r = joinCrew(currentState.code, account)) {
            is Result.Ok  -> {
                update { it.copy(isJoining = false) }
                analytics.track(AnalyticsEvent.CrewJoined(r.value.id, JoinMethod.INVITE_LINK))
                switchActive(r.value.id)
                emit(AcceptInviteEffect.Joined(r.value.id))
            }
            is Result.Err -> update { it.copy(isJoining = false, error = r.error) }
        }
    }
}
