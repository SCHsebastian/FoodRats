package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder

class HandleIncomingPushUseCase(
    private val bus: NotificationBus,
) {
    /** Called by the platform receiver after parsing the FCM payload into a domain Reminder. */
    suspend operator fun invoke(reminder: Reminder) = bus.publish(reminder)
}
