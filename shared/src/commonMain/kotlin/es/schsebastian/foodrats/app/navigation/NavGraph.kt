package es.schsebastian.foodrats.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.presentation.profile.ProfileScreen
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInScreen
import es.schsebastian.foodrats.feature.auth.presentation.topbar.TopBarAvatarViewModel
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerScreen
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsScreen
import es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailScreen
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedScreen
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealScreen
import es.schsebastian.foodrats.feature.ingredient.presentation.select.SelectIngredientsScreen
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateScreen
import es.schsebastian.foodrats.feature.notifications.presentation.permission.NotificationPermissionScreen
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NavGraph(navController: NavController = rememberNavController()) {
    // Outer (root) NavHost
    val controller = navController as NavHostController
    NavHost(navController = controller, startDestination = Route.Splash) {
        composable<Route.Splash> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    FrLogo(size = 128.dp)
                    FrProgressIndicator()
                }
            }
        }

        composable<Route.SignIn> {
            // After signin, RootNavViewModel resolves the next stage (NotificationPermission
            // → CrewPicker → Main) and drives navigation — keep the local callback as a
            // no-op so the two paths don't race to different destinations.
            SignInScreen(onSignedIn = {})
        }

        composable<Route.NotificationPermission> {
            // Continue is a no-op: marking the prompt flag drives RootNavViewModel to
            // emit NavigateTopLevel(CrewPicker | Main), which navigateTopLevel handles.
            NotificationPermissionScreen(onContinue = {})
        }

        composable<Route.CrewPicker> {
            CrewPickerScreen(onCrewSelected = { _ -> controller.navigateTopLevel(Route.Main) })
        }

        composable<Route.CrewSettings> { entry ->
            val args = entry.toRoute<Route.CrewSettings>()
            CrewSettingsScreen(
                crewId = args.crewId,
                onBack = { controller.popBackStack() },
                onLeft = { controller.navigateTopLevel(Route.CrewPicker) },
                onSwitch = { controller.navigate(Route.CrewPicker) },
                onDeleted = { controller.navigateTopLevel(Route.CrewPicker) },
            )
        }

        composable<Route.Profile> {
            ProfileScreen(onBack = { controller.popBackStack() })
        }

        composable<Route.Main> {
            MainScaffold(controller)
        }

        composable<Route.CaptureMeal> {
            CaptureMealScreen(
                onCaptured = {
                    controller.navigate(Route.ComposePlate) {
                        popUpTo<Route.CaptureMeal> { inclusive = true }
                    }
                },
                onCancelled = { controller.popBackStack() },
                onOpenSettings = { /* deferred */ },
            )
        }
        composable<Route.ComposePlate> {
            ComposePlateScreen(
                onPublishStarted = {
                    // Upload is now fire-and-forget on the background coordinator.
                    // Pop the compose chain immediately so the user lands on Feed
                    // and watches the top progress bar do its work.
                    controller.popBackStack(route = Route.Main, inclusive = false)
                },
                onEditIngredients = { controller.navigate(Route.SelectIngredients) },
            )
        }
        composable<Route.SelectIngredients> {
            SelectIngredientsScreen(onDone = { controller.popBackStack() })
        }
        composable<Route.MealDetail> { entry ->
            val args = entry.toRoute<Route.MealDetail>()
            MealDetailScreen(
                mealId = args.mealId,
                dayIso = args.dayIso,
                onBack = { controller.popBackStack() },
            )
        }
    }
}

/**
 * Replace the back stack with [route] — used for stage transitions (Splash → SignIn → CrewPicker → Main,
 * sign-out, session expiry). Pops everything up to and including the *current* bottom destination so
 * the transition lands on a single-entry stack regardless of where it fires from.
 *
 * Earlier versions used `popUpTo(graph.findStartDestination().id, inclusive = true)` (or
 * `popUpTo<Route.Splash>`) — both refer to the graph's *configured* start (Splash). That destination
 * leaves the back stack inclusively the first time we transition away, and from then on `popUpTo`
 * targets an ID that isn't in the stack — silently a no-op. The consequence: sign-out from a deep
 * screen kept the previous (authenticated) stack alive, RootNavViewModel updated its `stage` once,
 * and subsequent ticks saw "same stage" and stopped emitting.
 *
 * Reading the live back stack and popping inclusive of *its* bottom is robust regardless of how
 * many top-level transitions have already happened.
 */
fun NavHostController.navigateTopLevel(route: Route) {
    val bottomDestId = currentBackStack.value
        .firstOrNull { it.destination !is NavGraph }
        ?.destination?.id
    navigate(route) {
        bottomDestId?.let { popUpTo(it) { inclusive = true; saveState = false } }
        launchSingleTop = true
        restoreState = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(rootController: NavHostController) {
    val inner = rememberNavController()
    val activeCrew by koinInject<ActiveCrewProvider>().current.collectAsState(initial = null)
    val backStack by inner.currentBackStackEntryAsState()
    val isStats = backStack?.destination?.hasRoute<MainTab.Stats>() == true
    val titleKey = if (isStats) SharedStringKey.NavTabStats else SharedStringKey.NavTabFeed
    val topBarAvatarVm: TopBarAvatarViewModel = koinViewModel()
    val topBarAvatar by topBarAvatarVm.state.collectAsState()
    Scaffold(
        // Make the Scaffold's own surface match the bottom bar tint so the area
        // behind the Android system navigation bar (3-button or gesture pill)
        // doesn't show through as warm-cream/white when edge-to-edge is on.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(titleKey)) },
                navigationIcon = {
                    IconButton(onClick = { rootController.navigate(Route.Profile) }) {
                        FrAvatar(
                            initials = topBarAvatar.initials,
                            imageUrl = topBarAvatar.avatarUrl,
                            size = Sizes.avatarSm,
                            contentDescription = resolve(SharedStringKey.NavProfileCta),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
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
        bottomBar = {
            // primaryContainer tints the bar (including the area drawn behind the
            // system navigation bar via the bar's default windowInsets handling),
            // so the bottom of the screen matches the brand color on Android the
            // same way it does on iOS.
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = !isStats,
                    onClick = {
                        inner.navigate(MainTab.Feed) {
                            popUpTo<MainTab.Feed> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(FrIcons.Home, contentDescription = resolve(SharedStringKey.NavTabFeed)) },
                    label = { Text(resolve(SharedStringKey.NavTabFeed)) },
                )
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = false,
                    onClick = { rootController.navigate(Route.CaptureMeal) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = FrIcons.Camera,
                                contentDescription = resolve(SharedStringKey.NavCaptureCta),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                    ),
                )
                NavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = isStats,
                    onClick = {
                        inner.navigate(MainTab.Stats) {
                            popUpTo<MainTab.Feed> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(FrIcons.Stats, contentDescription = resolve(SharedStringKey.NavTabStats)) },
                    label = { Text(resolve(SharedStringKey.NavTabStats)) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = inner, startDestination = MainTab.Feed) {
                composable<MainTab.Feed> {
                    FeedScreen(
                        onPickCrewClick = { rootController.navigate(Route.CrewPicker) },
                        onMealClick = { mealId, dayIso ->
                            rootController.navigate(Route.MealDetail(mealId, dayIso))
                        },
                    )
                }
                composable<MainTab.Stats> { StatsScreen() }
            }
        }
    }
}
