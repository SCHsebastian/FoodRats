package es.schsebastian.foodrats.feature.crew.presentation.invite

import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.crew.domain.error.CrewError
import es.schsebastian.foodrats.feature.crew.domain.model.Crew

data class AcceptInviteState(
    /** The invite code carried by the deep link / QR. */
    val code: String = "",
    /** True while the preview crew is being resolved by code. */
    val isResolving: Boolean = false,
    /** The resolved crew (name + member count) for the preview, once known. */
    val crew: Crew? = null,
    /** True while a join is in flight. */
    val isJoining: Boolean = false,
    val error: CrewError? = null,
) : MviState

sealed interface AcceptInviteIntent : MviIntent {
    /** Resolve the preview crew for the current [AcceptInviteState.code]. */
    data object Resolve : AcceptInviteIntent
    data object Join : AcceptInviteIntent
    data object DismissError : AcceptInviteIntent
}

sealed interface AcceptInviteEffect : MviEffect {
    /** Join succeeded → land the user on the active crew's Main feed. */
    data class Joined(val crewId: CrewId) : AcceptInviteEffect
}
