package es.schsebastian.foodrats.feature.notifications.platform

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDateComponents
import platform.Foundation.NSError
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS scheduler: registers a repeating UNCalendarNotificationTrigger at 14:00 device-local.
 * Reschedule with the same Reminder.id replaces the existing request (identifier-based).
 *
 * Note: unlike Android, the pre-fire `HasPostedTodayPort` check is NOT performed on iOS in v1.
 * `UNCalendarNotificationTrigger` has no app-side hook between trigger firing and delivery; the
 * notification is shown unconditionally. A follow-up could swap to silent background pushes for
 * iOS, but that requires server involvement that we're avoiding for the daily reminder.
 */
class IosLocalReminderScheduler : LocalReminderScheduler {

    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        val content = UNMutableNotificationContent().apply {
            setTitle(reminder.title)
            setBody(reminder.body)
        }
        val components = NSDateComponents().apply {
            hour = HOUR_LOCAL
            minute = MINUTE_LOCAL
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents = components,
            repeats = true,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = reminder.id,
            content = content,
            trigger = trigger,
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(reminder.id))

        val nsError: NSError? = suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .addNotificationRequest(request) { error -> cont.resume(error) }
        }
        return if (nsError == null) {
            Result.success(Unit)
        } else {
            Result.failure(NotificationError.Schedule.Failed)
        }
    }

    override suspend fun cancel(reminderId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(reminderId))
    }

    private companion object {
        const val HOUR_LOCAL = 14L
        const val MINUTE_LOCAL = 0L
    }
}
