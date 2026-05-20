package es.schsebastian.foodrats.app.root

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.app.navigation.EventsEffect
import es.schsebastian.foodrats.app.navigation.NavGraph
import es.schsebastian.foodrats.app.navigation.navigateTopLevel
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodRatsApp() {
    val rootController = rememberNavController()
    val rootVm: RootNavViewModel = koinViewModel()
    EventsEffect(events = rootVm.effects) { eff ->
        FrLog.d(FrLog.Channel.RootNav) { "app: collected effect=$eff" }
        when (eff) {
            is RootNavEffect.NavigateTo -> {
                FrLog.d(FrLog.Channel.RootNav) {
                    "app: navigateTopLevel(${eff.route::class.simpleName})"
                }
                rootController.navigateTopLevel(eff.route)
            }
        }
    }
    FoodRatsTheme {
        NavGraph(navController = rootController)
    }
}
