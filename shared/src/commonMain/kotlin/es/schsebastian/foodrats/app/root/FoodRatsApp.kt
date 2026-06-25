package es.schsebastian.foodrats.app.root

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import es.schsebastian.foodrats.app.navigation.EventsEffect
import es.schsebastian.foodrats.app.navigation.NavGraph
import es.schsebastian.foodrats.app.navigation.Route
import es.schsebastian.foodrats.app.navigation.navigateTopLevel
import es.schsebastian.foodrats.app.connectivity.ConnectivityViewModel
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.app.locale.ProvideAppLocale
import es.schsebastian.foodrats.app.notifications.InAppPushBanner
import es.schsebastian.foodrats.core.designsystem.atoms.FrOfflineBanner
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.FrAccent
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AccentPalettePort
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.session.SessionRevalidator
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoodRatsApp() {
    val rootController = rememberNavController()
    val rootVm: RootNavViewModel = koinViewModel()

    // Live session-expiry detection (A2): on every foreground, force a token refresh so a server-side
    // disable/delete/revocation is discovered promptly. The revalidator signs out a revoked account,
    // which nulls SessionProvider.current → root nav routes to SignIn. A valid session is a cheap
    // no-op; transient failures are ignored (never sign a valid user out). Without this, revocation
    // goes unnoticed until the next ~hourly token refresh or app restart.
    val sessionRevalidator = koinInject<SessionRevalidator>()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sessionRevalidator, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sessionRevalidator.revalidate()
        }
    }

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
                // clobber the user's stack: if the base is already present we're in the right context,
                // so just push the leaf on top of where they are. Only when the base is absent (cold
                // start or a stashed link resumed) do we first establish it so Back from the leaf lands
                // there. launchSingleTop avoids stacking duplicates when the same notification is tapped
                // twice. The base is Main for the usual case, CrewPicker for a pre-crew invite (so Back
                // returns to the picker, not an empty Feed — see RootNavViewModel.emitNeedsCrew).
                val baseAlreadyInStack = rootController.currentBackStack.value.any { entry ->
                    when (eff.base) {
                        Route.CrewPicker -> entry.destination.hasRoute<Route.CrewPicker>()
                        else             -> entry.destination.hasRoute<Route.Main>()
                    }
                }
                if (!baseAlreadyInStack) {
                    FrLog.d(FrLog.Tags.RootNav) { "app: deepLink → establish ${eff.base::class.simpleName} base then push ${eff.route::class.simpleName}" }
                    rootController.navigateTopLevel(eff.base)
                } else {
                    FrLog.d(FrLog.Tags.RootNav) { "app: deepLink → push ${eff.route::class.simpleName} (base already in stack)" }
                }
                if (eff.route != eff.base) {
                    rootController.navigate(eff.route) { launchSingleTop = true }
                }
            }
        }
    }

    // Empty-stack floor — last-resort guard against the white/blank screen. The root NavHost must
    // never be left with no real destination: that renders nothing, and the next system BACK exits
    // the app. It can be reached when a transient top-level re-emit (RootNavViewModel re-fires on
    // stage flips) collapses the stack to a single pushed leaf (Profile/CrewSettings) via
    // navigateTopLevel's `popUpTo(bottom, inclusive)`, and the user then presses BACK — popBackStack()
    // returns false and leaves the stack empty. If that ever happens *after* we've landed at least
    // once, re-establish the authenticated base so the worst case degrades to "back to Feed" instead
    // of a stuck blank screen. Guarded on `hasLanded` so it never pre-empts the initial Splash→SignIn
    // landing for a signed-out cold start.
    LaunchedEffect(rootController) {
        var hasLanded = false
        rootController.currentBackStack.collect { stack ->
            val realEntries = stack.count { it.destination !is androidx.navigation.NavGraph }
            if (realEntries > 0) {
                hasLanded = true
            } else if (hasLanded) {
                FrLog.d(FrLog.Tags.RootNav) { "app: root back stack emptied — re-landing Main" }
                rootController.navigateTopLevel(Route.Main)
            }
        }
    }

    val themePort = koinInject<ThemeModePort>()
    val themeMode by themePort.mode.collectAsState(initial = ThemeMode.System)
    // Observed in-app language. Drives ProvideAppLocale below so picking En/Es actually flips
    // the UI text (the link that was previously missing — see app/locale/AppLocaleProvider).
    val localePort = koinInject<LocalePort>()
    val appLocale by localePort.locale.collectAsState(initial = AppLocale.System)
    val appLanguageTag = appLocale.tag.ifBlank { null }
    // Honor the user's stored theme choice. System follows the OS; Light/Dark are explicit overrides.
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Accent palette — collect the user's stored choice and map to the design-system enum.
    // The AccentPalette→FrAccent mapping lives here in :shared (presentation), not in
    // :core:designsystem, to keep the design system domain-free.
    val accentPort = koinInject<AccentPalettePort>()
    val accentPalette by accentPort.palette.collectAsState(initial = AccentPalette.Ember)
    val frAccent = accentPalette.toFrAccent()

    // Foreground/in-app push surface: the OS suppresses tray notifications while the app is
    // foregrounded, so an app-level snackbar is the only place a foreground push is shown. Lives
    // here at the root so it overlays every screen. The bus carries already-localized title/body
    // (resolved via NotificationStringKey in PushPayloadMapper.toReminder).
    val notificationBus = koinInject<NotificationBus>()
    val snackbarHostState = remember { SnackbarHostState() }

    // App-wide offline banner (offline-first §P1-T2): one connectivity signal surfaced at the root so
    // it overlays every screen. `visible = !isOnline` — hidden by default (assumes online until the
    // port reports otherwise). Message is resolved here through SharedStringKey, not in the atom.
    val connectivityVm: ConnectivityViewModel = koinViewModel()
    val isOnline by connectivityVm.isOnline.collectAsState()

    FoodRatsTheme(darkTheme = darkTheme, accent = frAccent) {
      // Re-keys the UI subtree on the chosen language so every resolve(...) re-resolves. The root
      // NavController is created above this block, so the back stack survives a language switch.
      ProvideAppLocale(languageTag = appLanguageTag) {
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
            // Banner above the nav content so the offline notice shows on every screen. The NavGraph
            // takes the remaining space (weight) and keeps owning its own bars/insets.
            Column(modifier = Modifier.fillMaxSize()) {
                // statusBarsPadding keeps the amber bar below the system status bar (it was
                // overlapping the clock). When the banner is shown it occupies that top inset's
                // vertical space, so we consume the status-bars inset for the NavGraph subtree below
                // — otherwise each screen's own Scaffold would pad the status bar a second time and
                // leave an empty gap under the banner. When online the banner is gone (0 height) and
                // we consume nothing, so screens inset themselves normally.
                FrOfflineBanner(
                    visible = !isOnline,
                    message = resolve(SharedStringKey.OfflineBanner),
                    modifier = Modifier.statusBarsPadding(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!isOnline) Modifier.consumeWindowInsets(WindowInsets.statusBars)
                            else Modifier,
                        ),
                ) {
                    NavGraph(navController = rootController)
                }
            }
        }
      }
    }
}

/**
 * Presentation-layer mapping from the domain [AccentPalette] enum to the design-system [FrAccent]
 * enum. Lives in `:shared` (presentation) so `:core:designsystem` stays domain-free.
 */
private fun AccentPalette.toFrAccent(): FrAccent = when (this) {
    AccentPalette.Ember -> FrAccent.Ember
    AccentPalette.Moss  -> FrAccent.Moss
    AccentPalette.Rust  -> FrAccent.Rust
    AccentPalette.Steel -> FrAccent.Steel
    AccentPalette.Berry -> FrAccent.Berry
}
