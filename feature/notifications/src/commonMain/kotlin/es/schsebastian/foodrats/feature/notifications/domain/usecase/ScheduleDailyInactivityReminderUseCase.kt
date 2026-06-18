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
 * Schedules a daily local notification at [time] device-local (defaults to 14:00 for backward
 * compatibility). The platform scheduler registers a repeating trigger so subsequent days fire
 * automatically. Re-invoking with the same [id] is idempotent (cancel-then-schedule). Pass a
 * distinct [id] per reminder slot to register several reminders at different times.
 */
class ScheduleDailyInactivityReminderUseCase(
    private val scheduler: LocalReminderScheduler,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend operator fun invoke(
        title: String,
        body: String,
        time: LocalTime = DEFAULT_TIME,
        id: String = DAILY_INACTIVITY_REMINDER_ID,
    ): Result<Unit, NotificationError.Schedule> {
        val now = clock.now().toLocalDateTime(zone)
        val baseDate = if (now.time < time) now.date else now.date.plus(DatePeriod(days = 1))
        val deliverAt = LocalDateTime(baseDate, time).toInstant(zone)
        val reminder = Reminder(
            id = id,
            kind = ReminderKind.StreakAtRisk,
            deliverAt = deliverAt,
            title = title,
            body = body,
        )
        scheduler.cancel(id)
        return scheduler.schedule(reminder)
    }

    companion object {
        const val DAILY_INACTIVITY_REMINDER_ID = "daily-inactivity-14h"
        val DEFAULT_TIME = LocalTime(hour = 14, minute = 0)
    }
}
