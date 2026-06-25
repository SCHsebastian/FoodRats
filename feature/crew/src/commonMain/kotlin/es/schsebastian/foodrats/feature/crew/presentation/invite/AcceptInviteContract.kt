package es.schsebastian.foodrats.feature.crew.presentation.invite

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
    /** True while the join request is being filed. */
    val isJoining: Boolean = false,
    /**
     * True once the join request was filed successfully. The screen then shows a "waiting for the
     * owner's approval" confirmation instead of the join CTA — the user is NOT yet a member.
     */
    val requestSent: Boolean = false,
    /** True while the requester's own pending request is being withdrawn. */
    val isCancelling: Boolean = false,
    val error: CrewError? = null,
) : MviState

sealed interface AcceptInviteIntent : MviIntent {
    /** Resolve the preview crew for the current [AcceptInviteState.code]. */
    data object Resolve : AcceptInviteIntent
    data object Join : AcceptInviteIntent
    /** Withdraw the just-filed join request (requester-side cancel) and return to the join CTA. */
    data object Cancel : AcceptInviteIntent
    data object DismissError : AcceptInviteIntent
}

/** No effects — the request-sent confirmation is driven by [AcceptInviteState.requestSent]. */
sealed interface AcceptInviteEffect : MviEffect
