package es.schsebastian.foodrats.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import es.schsebastian.foodrats.app.consent.ConsentScreen
import es.schsebastian.foodrats.app.recap.WeeklyStoryScreen
import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.ScreenName
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.app.legal.EulaGateScreen
import es.schsebastian.foodrats.app.legal.LegalDoc
import es.schsebastian.foodrats.app.legal.LegalDocScreen
import es.schsebastian.foodrats.feature.auth.presentation.profile.ProfileScreen
import es.schsebastian.foodrats.feature.auth.presentation.signin.SignInScreen
import es.schsebastian.foodrats.feature.auth.presentation.topbar.TopBarAvatarViewModel
import es.schsebastian.foodrats.feature.achievements.presentation.AchievementsScreen
import es.schsebastian.foodrats.feature.crew.presentation.invite.AcceptInviteScreen
import es.schsebastian.foodrats.feature.crew.presentation.picker.CrewPickerScreen
import es.schsebastian.foodrats.feature.crew.presentation.settings.CrewSettingsScreen
import es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailScreen
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedScreen
import es.schsebastian.foodrats.feature.moderation.presentation.blocked.BlockedUsersScreen
import es.schsebastian.foodrats.feature.meal.presentation.capture.CaptureMealScreen
import es.schsebastian.foodrats.feature.ingredient.presentation.select.SelectIngredientsScreen
import es.schsebastian.foodrats.feature.meal.presentation.compose.ComposePlateScreen
import es.schsebastian.foodrats.feature.meal.presentation.nudge.CaptureNudgeViewModel
import es.schsebastian.foodrats.feature.notifications.presentation.permission.NotificationPermissionScreen
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.feature.stats.presentation.passport.PassportScreen
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NavGraph(navController: NavController = rememberNavController()) {
    // Outer (root) NavHost
    val controller = navController as NavHostController
    TrackScreenViews(controller)
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
            SignInScreen(
                onSignedIn = {},
                // The embedded legal docs are Public, so they're reachable from the pre-auth
                // SignIn footer links. launchSingleTop so a double-tap doesn't stack duplicates.
                onOpenEula = { controller.navigate(Route.Eula) { launchSingleTop = true } },
                onOpenGuidelines = {
                    controller.navigate(Route.CommunityGuidelines) { launchSingleTop = true }
                },
            )
        }

        // Embedded EULA + Community Guidelines (UGC compliance §6). Public routes (readable
        // pre-auth from SignIn) and also reachable from Profile.
        composable<Route.Eula> {
            LegalDocScreen(doc = LegalDoc.EULA, onBack = { controller.popBackStack() })
        }
        composable<Route.CommunityGuidelines> {
            LegalDocScreen(doc = LegalDoc.COMMUNITY_GUIDELINES, onBack = { controller.popBackStack() })
        }

        composable<Route.NotificationPermission> {
            // Continue is a no-op: marking the prompt flag drives RootNavViewModel to
            // emit NavigateTopLevel(CrewPicker | Main), which navigateTopLevel handles.
            NotificationPermissionScreen(onContinue = {})
        }

        composable<Route.Consent> {
            // Continue is a no-op: writing the consent decision (grant/deny) drives
            // RootNavViewModel to emit NavigateTopLevel(Main), which navigateTopLevel handles.
            ConsentScreen(onDecided = {})
        }

        composable<Route.EulaGate> {
            // Acceptance is a no-op here: EulaGateScreen calls EulaPort.accept() directly via
            // koinInject. The acceptedVersion flow re-emits, RootNavViewModel clears NeedsEulaGate,
            // and emits NavigateTopLevel(Main) — no callback needed, same pattern as ConsentScreen.
            // The two doc-link callbacks navigate forward to the public legal-doc screens; back from
            // either returns to this gate (Apple G1.2 requires the EULA be readable at acceptance).
            EulaGateScreen(
                onReadEula = { controller.navigate(Route.Eula) { launchSingleTop = true } },
                onReadGuidelines = {
                    controller.navigate(Route.CommunityGuidelines) { launchSingleTop = true }
                },
            )
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
                onSwitch = { controller.navigate(Route.CrewPicker) { launchSingleTop = true } },
                onDeleted = { controller.navigateTopLevel(Route.CrewPicker) },
                // Canonical invite URL lives in `shared`'s DeepLinks (the URL contract owner);
                // passed down so :feature:crew stays free of a dependency on :shared.
                inviteUrlFor = { code -> DeepLinks.inviteUrl(code) },
            )
        }

        composable<Route.InvitePreview> { entry ->
            val args = entry.toRoute<Route.InvitePreview>()
            AcceptInviteScreen(
                code = args.code,
                onJoined = { controller.navigateTopLevel(Route.Main) },
                onBack = {
                    // From a cold-start invite the only thing behind us is Main (established by the
                    // deep-link handler); pop to it so "Not now" lands on the feed rather than exiting.
                    if (!controller.popBackStack()) controller.navigateTopLevel(Route.Main)
                },
            )
        }

        composable<Route.Profile> {
            ProfileScreen(
                onBack = { controller.popBackStack() },
                onOpenAchievements = { controller.navigate(Route.Achievements) { launchSingleTop = true } },
                onOpenEula = { controller.navigate(Route.Eula) { launchSingleTop = true } },
                onOpenGuidelines = {
                    controller.navigate(Route.CommunityGuidelines) { launchSingleTop = true }
                },
                onOpenBlockedUsers = {
                    controller.navigate(Route.BlockedUsers) { launchSingleTop = true }
                },
            )
        }

        composable<Route.Achievements> {
            AchievementsScreen(onBack = { controller.popBackStack() })
        }

        // Blocked-users list (UGC compliance §5), reached from Profile.
        composable<Route.BlockedUsers> {
            BlockedUsersScreen(onBack = { controller.popBackStack() })
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
        composable<Route.WeeklyStory> { entry ->
            val args = entry.toRoute<Route.WeeklyStory>()
            WeeklyStoryScreen(
                onDismiss = { controller.popBackStack() },
                source = if (args.fromNotification) {
                    DigestStorySource.NOTIFICATION
                } else {
                    DigestStorySource.IN_APP
                },
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

/**
 * Emits a `screen_view` analytics event on every destination change of [controller]. Firebase does
 * not auto-track screen views under Compose Multiplatform navigation, so this is the sanctioned manual
 * path. Used on both the root NavHost (top-level screens) and the Main scaffold's inner NavHost
 * (Feed/Stats tabs). The screen name is derived from the route *type*, never the arg-bearing route
 * string (see [toAnalyticsScreenName]). No-op until consent is granted (the gate is in the port).
 */
@Composable
private fun TrackScreenViews(controller: NavHostController) {
    val analytics = koinInject<AnalyticsPort>()
    LaunchedEffect(controller) {
        controller.currentBackStackEntryFlow.collect { entry ->
            analytics.track(AnalyticsEvent.ScreenViewed(entry.destination.toAnalyticsScreenName()))
        }
    }
}

/**
 * Maps a navigation [NavDestination] to a stable snake_case [ScreenName] from the route TYPE's simple
 * name (e.g. `…Route.MealDetail/{mealId}/{dayIso}` → `meal_detail`). Stripping the args keeps screen
 * cardinality low and the value within GA4's length cap.
 */
private fun NavDestination.toAnalyticsScreenName(): ScreenName {
    val raw = route ?: return ScreenName("unknown")
    val simple = raw.substringBefore('/').substringBefore('?').substringAfterLast('.')
    return ScreenName(simple.pascalToSnakeCase().ifEmpty { "unknown" })
}

private fun String.pascalToSnakeCase(): String = buildString {
    this@pascalToSnakeCase.forEachIndexed { index, c ->
        if (c.isUpperCase()) {
            if (index != 0) append('_')
            append(c.lowercaseChar())
        } else {
            append(c)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(rootController: NavHostController) {
    // The two tabs (Feed/Stats) are a flat switch, NOT a nested NavHost. A NavHost nested inside an
    // outer NavHost destination corrupts back-stack lifecycle restoration after process death — the
    // restored top destination (e.g. CrewSettings) never reaches RESUMED, so its
    // collectAsStateWithLifecycle never collects and the screen hangs on a blank spinner. Neither tab
    // keeps its own back stack (Feed pushes detail/picker through the root controller), so a saveable
    // selected-tab flag is all the state we need and it survives configuration change + process death.
    // Saveable selected tab as a MainTab: Feed | Passport | Stats. A flat switch (not a nested
    // NavHost) for the back-stack-restoration reason documented below.
    var selectedTab: MainTab by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver(
            save = { it::class.simpleName },
            restore = { name ->
                when (name) {
                    MainTab.Passport::class.simpleName -> MainTab.Passport
                    MainTab.Stats::class.simpleName -> MainTab.Stats
                    else -> MainTab.Feed
                }
            },
        ),
    ) { mutableStateOf(MainTab.Feed) }
    val analytics = koinInject<AnalyticsPort>()
    LaunchedEffect(selectedTab) {
        val name = when (selectedTab) {
            MainTab.Feed -> "feed"
            MainTab.Passport -> "passport"
            MainTab.Stats -> "stats"
        }
        analytics.track(AnalyticsEvent.ScreenViewed(ScreenName(name)))
    }
    val activeCrew by koinInject<ActiveCrewProvider>().current.collectAsState(initial = null)
    val topBarAvatarVm: TopBarAvatarViewModel = koinViewModel()
    val topBarAvatar by topBarAvatarVm.state.collectAsState()
    val captureNudgeVm: CaptureNudgeViewModel = koinViewModel()
    val captureNudge by captureNudgeVm.state.collectAsState()
    Scaffold(
        // Make the Scaffold's own surface match the bottom bar tint so the area
        // behind the Android system navigation bar (3-button or gesture pill)
        // doesn't show through as warm-cream/white when edge-to-edge is on.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainTopBar(
                titleKey = when (selectedTab) {
                    MainTab.Feed -> SharedStringKey.NavTabFeed
                    MainTab.Passport -> SharedStringKey.NavTabPassport
                    MainTab.Stats -> SharedStringKey.NavTabStats
                },
                avatarInitials = topBarAvatar.initials,
                avatarUrl = topBarAvatar.avatarUrl,
                showSettings = activeCrew != null,
                // launchSingleTop on every tap-driven push: a rapid double-tap (or a recomposition
                // that re-fires the click) must not stack a duplicate destination — the second tap
                // reuses the existing top entry instead of pushing a clone that the user then has to
                // Back through twice. Cheap idempotency for user-initiated navigation.
                onProfileClick = { rootController.navigate(Route.Profile) { launchSingleTop = true } },
                onSettingsClick = {
                    activeCrew?.let {
                        rootController.navigate(Route.CrewSettings(it.value)) { launchSingleTop = true }
                    }
                },
            )
        },
        bottomBar = {
            MainBottomBar(
                selected = selectedTab,
                hasPostedToday = captureNudge.hasPostedToday,
                onFeedClick = { selectedTab = MainTab.Feed },
                onPassportClick = { selectedTab = MainTab.Passport },
                onStatsClick = { selectedTab = MainTab.Stats },
                onCaptureClick = { rootController.navigate(Route.CaptureMeal) { launchSingleTop = true } },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                MainTab.Stats -> StatsScreen(
                    // In-app weekly-recap entry (roadmap §2.4). weekStart is informational — the
                    // client derives the recap from its own stats/achievements read paths.
                    onOpenRecap = {
                        rootController.navigate(
                            Route.WeeklyStory(weekStart = "", fromNotification = false),
                        ) { launchSingleTop = true }
                    },
                )
                MainTab.Passport -> PassportScreen()
                MainTab.Feed -> FeedScreen(
                    onPickCrewClick = { rootController.navigate(Route.CrewPicker) { launchSingleTop = true } },
                    onMealClick = { mealId, dayIso ->
                        rootController.navigate(Route.MealDetail(mealId, dayIso)) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
