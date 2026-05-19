package es.schsebastian.foodrats

import androidx.compose.ui.window.ComposeUIViewController
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.feature.auth.di.authIosModule
import es.schsebastian.foodrats.feature.feed.data.image.installFeedImageLoader
import es.schsebastian.foodrats.feature.notifications.di.notificationsIosModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * Entry point invoked from iosApp/ContentView.swift. Swift supplies:
 *   - [viewControllerProvider]: returns the current key-window root UIViewController,
 *     used by GoogleSignIn to present its picker.
 *   - [googleSignIn] / [googleSignOut]: delegate to GoogleSignInBridge static methods.
 *
 * The [googleSignIn] completion is invoked with `(idToken, accessToken, errorCode)` — on
 * iOS Firebase requires both idToken and accessToken (see GoogleAuthClient.ios.kt).
 */
fun MainViewController(
    viewControllerProvider: () -> UIViewController,
    googleSignIn: (
        UIViewController,
        (idToken: String?, accessToken: String?, errorCode: String?) -> Unit,
    ) -> Unit,
    googleSignOut: () -> Unit,
) = ComposeUIViewController(
    configure = {
        installFeedImageLoader()
        startKoin {
            modules(
                appModules + listOf(
                    notificationsIosModule,
                    authIosModule(viewControllerProvider, googleSignIn, googleSignOut),
                ),
            )
        }
    },
) {
    FoodRatsApp()
}
