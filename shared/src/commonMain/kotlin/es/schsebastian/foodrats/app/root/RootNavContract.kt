package es.schsebastian.foodrats.app.root

import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState

sealed interface RootStage {
    data object Splash : RootStage
    data object NeedsSignIn : RootStage
    data object NeedsCrew : RootStage
    data object Ready : RootStage
}

data class RootNavState(val stage: RootStage = RootStage.Splash) : MviState

sealed interface RootNavIntent : MviIntent

sealed interface RootNavEffect : MviEffect {
    data class NavigateTo(val route: Route) : RootNavEffect
}
