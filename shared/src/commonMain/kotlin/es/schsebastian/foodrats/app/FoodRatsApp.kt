package es.schsebastian.foodrats.app

import androidx.compose.runtime.Composable
import es.schsebastian.foodrats.app.nav.NavGraph
import es.schsebastian.foodrats.app.nav.Route
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

@Composable
fun FoodRatsApp(startDestination: Route = Route.SignIn) {
    FoodRatsTheme { NavGraph(startDestination) }
}
