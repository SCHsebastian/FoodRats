package es.schsebastian.foodrats.app.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.app.navigation.EventsEffect
import es.schsebastian.foodrats.app.navigation.NavGraph
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.app.navigation.navigateTopLevel
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
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

    FoodRatsTheme(darkTheme = darkTheme) {
        NavGraph(navController = rootController)
    }
}
