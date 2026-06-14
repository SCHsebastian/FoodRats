package es.schsebastian.foodrats.app.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.app.navigation.EventsEffect
import es.schsebastian.foodrats.app.navigation.NavGraph
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.app.navigation.navigateTopLevel
import es.schsebastian.foodrats.app.notifications.InAppPushBanner
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodRatsApp() {
    val rootController = rememberNavController()
    val rootVm: RootNavViewModel = koinViewModel()
    EventsEffect(events = rootVm.effects) { eff ->
        FrLog.d(FrLog.Tags.RootNav) { "app: collected effect=$eff" }
        when (eff) {
            is RootNavEffect.NavigateTopLevel -> {
                // Guard the Ready→Main landing against clobbering a screen the NavController
                // restored on its own. After process death the back stack is rebuilt to a deep
                // screen (e.g. MealDetail) *before* the session resolves; when it does, RootNav
                // emits NavigateTopLevel(Main). Popping to Main there would yank the user off the
                // restored screen — so if Main is already in the stack we're already in
                // authenticated content and the landing is a no-op. Other targets (SignIn on
                // sign-out, gates) always apply, so they clear the stack as before.
                val alreadyInAuthedContent = eff.route == Route.Main &&
                    rootController.currentBackStack.value.any { it.destination.hasRoute<Route.Main>() }
                if (alreadyInAuthedContent) {
                    FrLog.d(FrLog.Tags.RootNav) { "app: skip Main landing — already in authenticated content" }
                } else {
                    FrLog.d(FrLog.Tags.RootNav) { "app: navigateTopLevel(${eff.route::class.simpleName})" }
                    rootController.navigateTopLevel(eff.route)
                }
            }
            is RootNavEffect.NavigateDeepLink -> {
                // Mirror the NavigateTopLevel guard. A deep-link tap that arrives mid-flow must NOT
                // clobber the user's stack: if Main is already present we're in authenticated content,
                // so just push the leaf on top of where they are. Only when Main is absent (cold start
                // or a stashed link resumed at Ready) do we first establish the authenticated base so
                // Back from the leaf lands on Feed. launchSingleTop avoids stacking duplicates when the
                // same notification is tapped twice.
                val alreadyInAuthedContent =
                    rootController.currentBackStack.value.any { it.destination.hasRoute<Route.Main>() }
                if (!alreadyInAuthedContent) {
                    FrLog.d(FrLog.Tags.RootNav) { "app: deepLink → establish Main base then push ${eff.route::class.simpleName}" }
                    rootController.navigateTopLevel(Route.Main)
                } else {
                    FrLog.d(FrLog.Tags.RootNav) { "app: deepLink → push ${eff.route::class.simpleName} (already in authenticated content)" }
                }
                if (eff.route != Route.Main) {
                    rootController.navigate(eff.route) { launchSingleTop = true }
                }
            }
        }
    }

    val themePort = koinInject<ThemeModePort>()
    val themeMode by themePort.mode.collectAsState(initial = ThemeMode.System)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Foreground/in-app push surface: the OS suppresses tray notifications while the app is
    // foregrounded, so an app-level snackbar is the only place a foreground push is shown. Lives
    // here at the root so it overlays every screen. The bus carries already-localized title/body
    // (resolved via NotificationStringKey in PushPayloadMapper.toReminder).
    val notificationBus = koinInject<NotificationBus>()
    val snackbarHostState = remember { SnackbarHostState() }

    FoodRatsTheme(darkTheme = darkTheme) {
        InAppPushBanner(bus = notificationBus, snackbarHostState = snackbarHostState)
        Scaffold(
            // Host whose only job is to position the SnackbarHost above every screen; the NavGraph
            // owns its own bars/insets (MainScaffold), so we let it fill the body and do NOT apply
            // innerPadding to it (that would double-pad). The snackbar floats over.
            //
            // The container is painted with the app background (NOT transparent) so that during a
            // navigation transition — Compose Navigation crossfades destinations, and the incoming
            // screen may not paint its first frame instantly — the gap never reveals the platform
            // window background (`Theme.Material` dark-gray), which showed up as an intermittent
            // "gray screen" when opening Profile/CrewSettings. Mirrors MainScaffold's containerColor.
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavGraph(navController = rootController)
            }
        }
    }
}
