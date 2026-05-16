package es.schsebastian.foodrats.app.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInScreen
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealScreen
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateScreen
import es.schsebastian.foodrats.feature.meal.presentation.publish.PublishMealScreen

@Composable
fun NavGraph(startDestination: Route = Route.SignIn) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Route.SignIn> {
            SignInScreen(onSignedIn = { navController.navigate(Route.CaptureMeal) })
        }
        composable<Route.CaptureMeal> {
            CaptureMealScreen(
                onCaptured = { navController.navigate(Route.ComposePlate) },
                onOpenSettings = { /* platform-specific */ },
            )
        }
        composable<Route.ComposePlate> {
            ComposePlateScreen(onComposed = { navController.navigate(Route.PublishMeal) })
        }
        composable<Route.PublishMeal> {
            PublishMealScreen(onPublished = {
                navController.popBackStack(Route.SignIn, inclusive = false)
                navController.navigate(Route.CaptureMeal)
            })
        }
        composable<Route.Feed> { }
        composable<Route.CrewPicker> { }
        composable<Route.Stats> { }
        composable<Route.NotificationPermission> { }
    }
}
