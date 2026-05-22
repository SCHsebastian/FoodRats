package es.schsebastian.foodrats.feature.auth.presentation.profile

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

actual val opensSystemSettingsForLanguage: Boolean = true

/**
 * Opens the FoodRats page inside iOS Settings. From there the user can change
 * the app language using the native per-app language picker (iOS 13+).
 *
 * Uses the modern `openURL:options:completionHandler:` API — the deprecated
 * single-arg `openURL:` is a silent no-op on recent iOS.
 */
actual fun openAppLanguageSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(
        url = url,
        options = emptyMap<Any?, Any>(),
        completionHandler = null,
    )
}
