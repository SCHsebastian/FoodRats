package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Schedules a daily local notification at 14:00 device-local. The platform scheduler is expected
 * to register a repeating trigger so subsequent days fire automatically without re-scheduling.
 * Caller invokes once after permission grant / app launch — re-invoking is idempotent.
 */
class ScheduleDailyInactivityReminderUseCase(
    private val scheduler: LocalReminderScheduler,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend operator fun invoke(
        title: String,
        body: String,
    ): Result<Unit, NotificationError.Schedule> {
        val now = clock.now().toLocalDateTime(zone)
        val targetTime = LocalTime(hour = TARGET_HOUR_LOCAL, minute = TARGET_MINUTE_LOCAL)
        val baseDate = if (now.time < targetTime) now.date else now.date.plus(DatePeriod(days = 1))
        val deliverAt = LocalDateTime(baseDate, targetTime).toInstant(zone)
        val reminder = Reminder(
            id = DAILY_INACTIVITY_REMINDER_ID,
            kind = ReminderKind.StreakAtRisk,
            deliverAt = deliverAt,
            title = title,
            body = body,
        )
        scheduler.cancel(DAILY_INACTIVITY_REMINDER_ID)
        return scheduler.schedule(reminder)
    }

    companion object {
        const val DAILY_INACTIVITY_REMINDER_ID = "daily-inactivity-14h"
        private const val TARGET_HOUR_LOCAL = 14
        private const val TARGET_MINUTE_LOCAL = 0
    }
}
