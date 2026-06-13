package es.schsebastian.foodrats.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import es.schsebastian.foodrats.feature.notifications.data.push.PushPayloadMapper
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class FoodRatsFirebaseMessagingService : FirebaseMessagingService() {

    private val bus: NotificationBus by inject()
    private val mapper: PushPayloadMapper by inject()
    // SupervisorJob isolates one publish failure from the others; cancelled in onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        // Token registration is observed reactively via AndroidFcmTokenProvider; no-op here.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            val reminder = mapper.toReminder(message.data) ?: Reminder(
                id = message.messageId ?: System.currentTimeMillis().toString(),
                kind = ReminderKind.NewMealPost,
                deliverAt = Clock.System.now(),
                title = message.notification?.title.orEmpty(),
                body = message.notification?.body.orEmpty(),
                payload = ReminderPayload.None,
            )
            bus.publish(reminder)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
