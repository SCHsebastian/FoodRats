package es.schsebastian.foodrats

import es.schsebastian.foodrats.feature.notifications.data.push.PushPayloadMapper
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Called by Swift AppDelegate when a remote notification arrives in the foreground
 * (`userNotificationCenter(_:willPresent:)`); taps are routed through [IosDeepLinkBridge]
 * instead. Parses the data dictionary
 * via [PushPayloadMapper] and publishes a [es.schsebastian.foodrats.feature.notifications.domain.model.Reminder]
 * to [NotificationBus] so the root composable shows the in-app banner.
 *
 * Note: this runs in addition to the OS-level lock-screen / notification-center display, which
 * iOS handles automatically from the `notification` block in the FCM payload. This bridge is
 * only for in-app routing.
 *
 * A push delivered during the cold-start launch window (before Koin starts) is deferred and
 * replayed via [IosBridgeGate] instead of crashing at the ObjC boundary.
 */
@Suppress("unused")
object IosNotificationBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun publish(data: Map<String, String>) {
        IosBridgeGate.runWhenReady {
            val koin = KoinPlatform.getKoin()
            val mapper = koin.get<PushPayloadMapper>()
            val bus = koin.get<NotificationBus>()
            scope.launch {
                val reminder = mapper.toReminder(data) ?: return@launch
                bus.publish(reminder)
            }
        }
    }
}
