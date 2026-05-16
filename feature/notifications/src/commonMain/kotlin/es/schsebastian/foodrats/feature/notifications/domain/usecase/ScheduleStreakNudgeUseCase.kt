package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.DeliveryWindow
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Schedules a local notification for **the next day at the delivery window** (default 19:00 local).
 * If today's nudge is still pending, scheduling a new one replaces it (same reminderId).
 */
class ScheduleStreakNudgeUseCase(
    private val scheduler: LocalReminderScheduler,
    private val clock: Clock,
    private val zone: TimeZone,
) {
    suspend operator fun invoke(
        title: String,
        body: String,
        window: DeliveryWindow = DeliveryWindow.Default,
    ): Result<Unit, NotificationError.Schedule> {
        val now = clock.now().toLocalDateTime(zone)
        val tomorrowDate = now.date.plus(DatePeriod(days = 1))
        val deliverAt = LocalDateTime(tomorrowDate, window.localTime).toInstant(zone)
        val reminder = Reminder(
            id = STREAK_REMINDER_ID,
            kind = ReminderKind.StreakAtRisk,
            deliverAt = deliverAt,
            title = title,
            body = body,
        )
        // Cancel-then-schedule = replace.
        scheduler.cancel(STREAK_REMINDER_ID)
        return scheduler.schedule(reminder)
    }

    companion object { const val STREAK_REMINDER_ID = "streak-nudge" }
}
