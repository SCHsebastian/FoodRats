package es.schsebastian.foodrats

import androidx.compose.ui.window.ComposeUIViewController
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.clearLegacyDevCrewIfPresent
import es.schsebastian.foodrats.core.data.di.crashIosModule
import es.schsebastian.foodrats.core.data.di.shareIosModule
import es.schsebastian.foodrats.feature.auth.di.authIosModule
import es.schsebastian.foodrats.core.data.image.installImageLoader
import es.schsebastian.foodrats.feature.notifications.di.notificationsIosModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * Entry point invoked from iosApp/ContentView.swift. Swift supplies:
 *   - [viewControllerProvider]: returns the current key-window root UIViewController,
 *     used by GoogleSignIn to present its picker.
 *   - [googleSignIn] / [googleSignOut]: delegate to GoogleSignInBridge static methods.
 *
 * The [googleSignIn] completion is invoked with `(idToken, accessToken, errorCode)` — on
 * iOS Firebase requires both idToken and accessToken (see GoogleAuthClient.ios.kt).
 *
 * [crashRecordNonFatal] / [crashLog] bridge to CrashlyticsBridge — Firebase Crashlytics has no
 * KMP binding and is resolved via SPM in Xcode, so it must be called from Swift.
 */
fun MainViewController(
    viewControllerProvider: () -> UIViewController,
    googleSignIn: (
        UIViewController,
        (idToken: String?, accessToken: String?, errorCode: String?) -> Unit,
    ) -> Unit,
    googleSignOut: () -> Unit,
    crashRecordNonFatal: (domain: String, message: String) -> Unit,
    crashLog: (String) -> Unit,
) = ComposeUIViewController(
    configure = {
        installImageLoader()
        startKoin {
            modules(
                appModules + listOf(
                    notificationsIosModule,
                    shareIosModule,
                    authIosModule(viewControllerProvider, googleSignIn, googleSignOut),
                    crashIosModule(crashRecordNonFatal, crashLog),
                ),
            )
        }
        // Self-healing migration: see Android equivalent in FoodRatsApplication.onCreate.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            KoinPlatform.getKoin().get<AppPreferences>().clearLegacyDevCrewIfPresent()
        }
    },
) {
    FoodRatsApp()
}
