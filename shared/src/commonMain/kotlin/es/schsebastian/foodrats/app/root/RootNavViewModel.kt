package es.schsebastian.foodrats.app.root

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RootNavViewModel(
    private val session: SessionProvider,
    private val activeCrew: ActiveCrewProvider,
) : MviViewModel<RootNavState, RootNavIntent, RootNavEffect>(RootNavState()) {

    init {
        viewModelScope.launch {
            combine(session.current, activeCrew.current) { sess, crewId -> sess to crewId }
                .collect { (sess, crewId) ->
                    val nextStage = when {
                        sess == null   -> RootStage.NeedsSignIn
                        crewId == null -> RootStage.NeedsCrew
                        else           -> RootStage.Ready
                    }
                    val target = when (nextStage) {
                        RootStage.Splash       -> null   // unreachable here
                        RootStage.NeedsSignIn  -> Route.SignIn
                        RootStage.NeedsCrew    -> Route.CrewPicker
                        RootStage.Ready        -> Route.Main
                    }
                    if (currentState.stage != nextStage) {
                        update { it.copy(stage = nextStage) }
                        target?.let { emit(RootNavEffect.NavigateTo(it)) }
                    }
                }
        }
    }

    override suspend fun handle(intent: RootNavIntent) = Unit
}
