package es.schsebastian.foodrats

import es.schsebastian.foodrats.feature.notifications.data.push.PushPayloadMapper
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Called by Swift AppDelegate when a remote notification arrives in the foreground OR when the
 * user taps a notification that launched the app from the background. Parses the data dictionary
 * via [PushPayloadMapper] and publishes a [es.schsebastian.foodrats.feature.notifications.domain.model.Reminder]
 * to [NotificationBus] so the root composable shows the in-app banner.
 *
 * Note: this runs in addition to the OS-level lock-screen / notification-center display, which
 * iOS handles automatically from the `notification` block in the FCM payload. This bridge is
 * only for in-app routing.
 */
@Suppress("unused")
object IosNotificationBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun publish(data: Map<String, String>) {
        val koin = KoinPlatform.getKoin()
        val mapper = koin.get<PushPayloadMapper>()
        val bus = koin.get<NotificationBus>()
        val reminder = mapper.toReminder(data) ?: return
        scope.launch { bus.publish(reminder) }
    }
}
