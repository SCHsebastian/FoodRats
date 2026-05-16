package es.schsebastian.foodrats.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.android.ext.android.inject

class FoodRatsFirebaseMessagingService : FirebaseMessagingService() {

    private val bus: NotificationBus by inject()
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onNewToken(token: String) {
        // Token registration is observed reactively via AndroidFcmTokenProvider; no-op here for MVP.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val reminder = Reminder(
            id = data["id"] ?: message.messageId ?: System.currentTimeMillis().toString(),
            kind = runCatching { ReminderKind.valueOf(data["kind"].orEmpty()) }
                .getOrDefault(ReminderKind.StreakAtRisk),
            deliverAt = Clock.System.now(),
            title = data["title"] ?: message.notification?.title.orEmpty(),
            body  = data["body"]  ?: message.notification?.body.orEmpty(),
        )
        scope.launch { bus.publish(reminder) }
    }
}
