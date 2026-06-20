package es.schsebastian.foodrats.app.root

import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

sealed interface RootStage {
    data object Splash : RootStage
    data object NeedsSignIn : RootStage
    data object NeedsNotificationPermission : RootStage
    data object NeedsConsent : RootStage
    data object NeedsCrew : RootStage
    /**
     * The user has never accepted the current EULA version (or the version was bumped since their
     * last acceptance). Gated AFTER [NeedsConsent] so consent is always the last analytics step;
     * the EULA gate leads directly to [Ready] once accepted (UGC compliance §6 re-acceptance).
     */
    data object NeedsEulaGate : RootStage
    data object Ready : RootStage
}

/**
 * @param stage current onboarding/auth stage; the single source of truth that drives top-level
 *   navigation.
 * @param pendingDeepLink a [Route.Protected] destination requested via a deep link while the user
 *   was not yet [RootStage.Ready]. Held until the auth/crew/notification gates clear, then
 *   navigated to (intercept-then-resume). Null when nothing is pending.
 */
data class RootNavState(
    val stage: RootStage = RootStage.Splash,
    val pendingDeepLink: Route? = null,
) : MviState

sealed interface RootNavIntent : MviIntent

sealed interface RootNavEffect : MviEffect {
    /** Replace the whole back stack with [route] — stage transitions, sign-out, expiry. */
    data class NavigateTopLevel(val route: Route) : RootNavEffect

    /** Land on the authenticated base ([Route.Main]) then push [route] — a deep link to a leaf. */
    data class NavigateDeepLink(val route: Route) : RootNavEffect
}
