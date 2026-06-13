package es.schsebastian.foodrats.core.data.share

import es.schsebastian.foodrats.core.domain.share.ShareController

/**
 * [ShareController] for iOS. UIKit's `UIActivityViewController` must be presented from a live
 * view controller, which Kotlin/Native can't reach cleanly — so, like GoogleSignIn and
 * Crashlytics, we bridge through a Swift lambda supplied at app startup
 * (see iosApp/ShareBridge.swift + MainViewController).
 *
 * @param shareBridge called with the text to share; the Swift side builds a
 *   `UIActivityViewController` and presents it from the top-most view controller.
 */
class ShareControllerIos(
    private val shareBridge: (String) -> Unit,
) : ShareController {
    override fun shareText(text: String) = shareBridge(text)
}
