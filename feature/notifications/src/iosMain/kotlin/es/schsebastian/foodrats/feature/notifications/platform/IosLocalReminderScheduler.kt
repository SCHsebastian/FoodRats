package es.schsebastian.foodrats.feature.notifications.platform

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSError
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS scheduler: registers a repeating UNCalendarNotificationTrigger at the reminder's device-local
 * hour/minute, derived from [Reminder.deliverAt] (so user-chosen times and multiple reminders work).
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
        // Derive the daily hour/minute from the next-occurrence Instant the use case computed.
        val local = reminder.deliverAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents().apply {
            hour = local.hour.toLong()
            minute = local.minute.toLong()
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
}
