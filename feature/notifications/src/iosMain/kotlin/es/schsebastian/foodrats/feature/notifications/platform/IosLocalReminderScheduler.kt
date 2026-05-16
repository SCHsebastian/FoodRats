package es.schsebastian.foodrats.feature.notifications.platform

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

class IosLocalReminderScheduler : LocalReminderScheduler {

    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        val content = UNMutableNotificationContent().apply {
            setTitle(reminder.title)
            setBody(reminder.body)
        }
        val date = NSDate.dateWithTimeIntervalSince1970(reminder.deliverAt.toEpochMilliseconds() / 1000.0)
        val units = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                    NSCalendarUnitHour or NSCalendarUnitMinute
        val components = NSCalendar.currentCalendar.components(units, date)
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = reminder.id,
            content = content,
            trigger = trigger,
        )
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { /* error ignored */ }
        return Result.success(Unit)
    }

    override suspend fun cancel(reminderId: String) {
        UNUserNotificationCenter.currentNotificationCenter()
            .removePendingNotificationRequestsWithIdentifiers(listOf(reminderId))
    }
}
