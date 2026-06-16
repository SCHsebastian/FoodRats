package es.schsebastian.foodrats

import androidx.compose.ui.window.ComposeUIViewController
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.app.root.FoodRatsApp
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.clearLegacyDevCrewIfPresent
import es.schsebastian.foodrats.core.data.di.analyticsIosModule
import es.schsebastian.foodrats.core.data.di.configIosModule
import es.schsebastian.foodrats.core.data.di.crashIosModule
import es.schsebastian.foodrats.core.data.di.locationIosModule
import es.schsebastian.foodrats.core.data.di.shareIosModule
import es.schsebastian.foodrats.core.data.di.storyShareIosModule
import es.schsebastian.foodrats.core.data.telemetry.CrashReporterLogSink
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.auth.di.authIosModule
import es.schsebastian.foodrats.core.data.image.installImageLoader
import es.schsebastian.foodrats.feature.meal.di.mealIosModule
import es.schsebastian.foodrats.feature.mealai.di.mealAiIosModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsIosModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import platform.Foundation.NSData
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
 *
 * [classifyPlate] bridges to MediaPipeClassifierBridge — MediaPipe Tasks Vision on iOS ships as a
 * CocoaPod / XCFramework integrated in Xcode, so inference runs in Swift and is bridged into Kotlin.
 * The completion is invoked with `(labels, errorCode)` — exactly one side is non-null; each label is
 * a primitive `"<dishSlug>|<confidence>"` string (kept primitive so the boundary needs no exported
 * `:core:domain` type in the ObjC header).
 *
 * [share] bridges to ShareBridge — UIKit's `UIActivityViewController` must be presented from a live
 * view controller, so the share sheet is built and presented in Swift (see iosApp/ShareBridge.swift).
 *
 * [storyShare] bridges to StoryShareBridge — the shareable story-card PNG is handed to Instagram
 * Stories (UIPasteboard background image + `instagram-stories://share`) or, when Instagram is absent,
 * a `UIActivityViewController`. Presentation must happen from a live view controller on the main
 * thread, so it is built in Swift (see iosApp/StoryShareBridge.swift). Returns a status code:
 * 0 = Instagram opened, 1 = fallback sheet, 2 = failed.
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
    classifyPlate: (
        jpeg: NSData,
        (labels: List<String>?, errorCode: String?) -> Unit,
    ) -> Unit,
    share: (String) -> Unit,
    storyShare: (imagePng: ByteArray) -> Int,
    analyticsLogEvent: (name: String, params: Map<String, Any>) -> Unit,
    analyticsSetUserId: (accountId: String?) -> Unit,
    analyticsSetUserProperty: (name: String, value: String) -> Unit,
    analyticsSetConsent: (granted: Boolean) -> Unit,
    analyticsReset: () -> Unit,
) = ComposeUIViewController(
    configure = {
        installImageLoader()
        // Idempotency guard: a view-controller recreation (e.g. scene reattach) would otherwise
        // re-enter startKoin and throw KoinApplicationAlreadyStartedException. Start exactly once.
        if (KoinPlatform.getKoinOrNull() == null) {
            startKoin {
                modules(
                    appModules + listOf(
                        notificationsIosModule,
                        mealIosModule,
                        mealAiIosModule(classifyPlate),
                        shareIosModule(share),
                        storyShareIosModule(storyShare),
                        authIosModule(viewControllerProvider, googleSignIn, googleSignOut),
                        crashIosModule(crashRecordNonFatal, crashLog),
                        analyticsIosModule(
                            analyticsLogEvent,
                            analyticsSetUserId,
                            analyticsSetUserProperty,
                            analyticsSetConsent,
                            analyticsReset,
                        ),
                        configIosModule,
                        locationIosModule,
                    ),
                )
            }
        }
        // Route FrLog warnings/errors to the Crashlytics-backed CrashReporter. Crashlytics
        // collection is already disabled in debug on the Swift side (`#if DEBUG`), so this is
        // effectively a no-op there; the debug println path is unaffected.
        FrLog.installSink(CrashReporterLogSink(KoinPlatform.getKoin().get<CrashReporter>()))

        // Self-healing migration: see Android equivalent in FoodRatsApplication.onCreate.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            KoinPlatform.getKoin().get<AppPreferences>().clearLegacyDevCrewIfPresent()
        }
    },
) {
    FoodRatsApp()
}
