package es.schsebastian.foodrats

import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import org.koin.mp.KoinPlatform

/**
 * Called by Swift when iOS hands the app an external URL — a Universal Link
 * (`application(_:continue:restorationHandler:)` with an `NSUserActivityTypeBrowsingWeb` activity)
 * or a custom-scheme URL (`onOpenURL` / `application(_:open:options:)`). Forwards the raw URI to
 * the shared [DeepLinkBus], which `RootNavViewModel` parses and routes.
 *
 * Mirrors [IosNotificationBridge]; called as `IosDeepLinkBridge.shared.receive(uri:)` from Swift.
 */
@Suppress("unused")
object IosDeepLinkBridge {
    fun receive(uri: String) {
        KoinPlatform.getKoin().get<DeepLinkBus>().publish(uri)
    }
}
