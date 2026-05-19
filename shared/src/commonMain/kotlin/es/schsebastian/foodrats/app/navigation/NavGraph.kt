package es.schsebastian.foodrats.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInScreen
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerScreen
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsScreen
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedScreen
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealScreen
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateScreen
import es.schsebastian.foodrats.feature.meal.presentation.publish.PublishMealScreen
import es.schsebastian.foodrats.feature.notifications.presentation.permission.NotificationPermissionScreen
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsScreen
import org.koin.compose.koinInject

@Composable
fun NavGraph(navController: NavController = rememberNavController()) {
    // Outer (root) NavHost
    val controller = navController as NavHostController
    NavHost(navController = controller, startDestination = Route.Splash) {
        composable<Route.Splash> { /* empty — RootNavViewModel will navigate away */ }

        composable<Route.SignIn> {
            SignInScreen(onSignedIn = { controller.navigateTopLevel(Route.CrewPicker) })
        }

        composable<Route.NotificationPermission> {
            NotificationPermissionScreen(onContinue = { controller.popBackStack() })
        }

        composable<Route.CrewPicker> {
            CrewPickerScreen(onCrewSelected = { _ -> controller.navigateTopLevel(Route.Main) })
        }

        composable<Route.CrewSettings> { entry ->
            val args = entry.toRoute<Route.CrewSettings>()
            CrewSettingsScreen(
                crewId = args.crewId,
                onLeft = { controller.navigateTopLevel(Route.CrewPicker) },
                onSwitch = { controller.navigate(Route.CrewPicker) },
                onDeleted = { controller.navigateTopLevel(Route.CrewPicker) },
            )
        }

        composable<Route.Main> {
            MainScaffold(controller)
        }

        composable<Route.CaptureMeal> {
            CaptureMealScreen(
                onCaptured = { controller.navigate(Route.ComposePlate) },
                onOpenSettings = { /* deferred */ },
            )
        }
        composable<Route.ComposePlate> {
            ComposePlateScreen(onComposed = { controller.navigate(Route.PublishMeal) })
        }
        composable<Route.PublishMeal> {
            PublishMealScreen(onPublished = {
                // Clear the capture → compose → publish chain and return to Main (Feed tab).
                controller.popBackStack(route = Route.Main, inclusive = false)
            })
        }
    }
}

/** "Navigate top-level" replaces the back-stack, used for stage transitions. */
fun NavHostController.navigateTopLevel(route: Route) {
    navigate(route) {
        popUpTo<Route.Splash> { inclusive = true; saveState = false }
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(rootController: NavHostController) {
    val inner = rememberNavController()
    val activeCrew by koinInject<ActiveCrewProvider>().current.collectAsState(initial = null)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("") },
                actions = {
                    activeCrew?.let { crewId ->
                        IconButton(
                            onClick = { rootController.navigate(Route.CrewSettings(crewId.value)) },
                        ) {
                            Icon(
                                imageVector = FrIcons.Settings,
                                contentDescription = resolve(SharedStringKey.NavSettingsCta),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { rootController.navigate(Route.CaptureMeal) },
            ) {
                Icon(
                    imageVector = FrIcons.Camera,
                    contentDescription = resolve(SharedStringKey.NavCaptureCta),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            val backStack by inner.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = current?.contains("Feed") == true,
                    onClick = {
                        inner.navigate(MainTab.Feed) {
                            popUpTo<MainTab.Feed> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(FrIcons.Home, contentDescription = "Feed") },
                    label = { Text("Feed") },
                )
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = current?.contains("Stats") == true,
                    onClick = {
                        inner.navigate(MainTab.Stats) {
                            popUpTo<MainTab.Feed> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(FrIcons.Stats, contentDescription = "Stats") },
                    label = { Text("Stats") },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = inner, startDestination = MainTab.Feed) {
                composable<MainTab.Feed> {
                    FeedScreen(
                        onPickCrewClick = { rootController.navigate(Route.CrewPicker) },
                    )
                }
                composable<MainTab.Stats> { StatsScreen() }
            }
        }
    }
}
