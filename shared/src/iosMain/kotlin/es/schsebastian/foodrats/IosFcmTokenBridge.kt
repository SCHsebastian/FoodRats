package es.schsebastian.foodrats

import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Called by the Swift `AppDelegate`'s `MessagingDelegate.messaging(_:didReceiveRegistrationToken:)`
 * whenever FCM mints or rotates this device's registration token.
 *
 * iOS supplies the APNs device token to Firebase **asynchronously** after launch, so the FCM token
 * that [TokenRegistrationPort.registerCurrentDeviceToken] reads at sign-in can still be null (APNs
 * not ready yet) — in which case no `accounts/{uid}/devices/{token}` doc is ever written and the
 * server has nothing to deliver comment / new-meal pushes to. This callback fires exactly when a
 * token becomes available (and on every later rotation), so we re-run the idempotent registration
 * here to close that gap.
 *
 * Safe to call before sign-in or before Koin starts: registration no-ops (Unavailable) when not
 * signed in, and a pre-Koin callback is swallowed — FCM re-delivers the token on the next launch.
 */
@Suppress("unused")
object IosFcmTokenBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun tokenRefreshed() {
        val koin = runCatching { KoinPlatform.getKoin() }.getOrNull() ?: return
        val port = koin.get<TokenRegistrationPort>()
        scope.launch { port.registerCurrentDeviceToken() }
    }
}
