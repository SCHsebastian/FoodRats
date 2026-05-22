package es.schsebastian.foodrats.feature.notifications.platform

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import java.util.concurrent.TimeUnit
import kotlinx.datetime.Clock as KxClock

/**
 * Android scheduler: registers a daily-recurring PeriodicWorkRequest. The initial-delay lands at
 * the next 14:00 device-local (computed by the caller and passed in via [Reminder.deliverAt]);
 * subsequent fires happen every 24h via WorkManager's periodic scheduling. The worker itself
 * (DailyInactivityWorker) consults [HasPostedTodayPort] before posting the notification.
 */
class AndroidLocalReminderScheduler(
    private val context: Context,
) : LocalReminderScheduler {

    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        val nowMs = KxClock.System.now().toEpochMilliseconds()
        val initialDelayMs = reminder.deliverAt.toEpochMilliseconds() - nowMs
        if (initialDelayMs <= 0) return Result.failure(NotificationError.Schedule.Failed)
        val req = PeriodicWorkRequestBuilder<DailyInactivityWorker>(
            repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 15, flexTimeIntervalUnit = TimeUnit.MINUTES,
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DailyInactivityWorker.KEY_TITLE, reminder.title)
                    .putString(DailyInactivityWorker.KEY_BODY, reminder.body)
                    .build(),
            )
            .addTag(reminder.id)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            reminder.id,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
        return Result.success(Unit)
    }

    override suspend fun cancel(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(reminderId)
    }
}
