package es.schsebastian.foodrats

import androidx.compose.ui.window.ComposeUIViewController
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.feature.notifications.di.notificationsIosModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(appModules + listOf(notificationsIosModule))
            // Auth iOS-specific: GoogleAuthClient(UIViewController-provider) — wired by Swift via a
            // small helper. iOS sign-in is non-functional until the Swift bridge is fully connected;
            // Android works end-to-end. Iterate on iOS after Android smoke test passes.
        }
    },
) {
    FoodRatsApp()
}
