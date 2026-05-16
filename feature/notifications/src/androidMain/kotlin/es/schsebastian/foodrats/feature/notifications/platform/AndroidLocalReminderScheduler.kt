package es.schsebastian.foodrats.feature.notifications.platform

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import java.util.concurrent.TimeUnit
import kotlinx.datetime.Clock as KxClock

class AndroidLocalReminderScheduler(
    private val context: Context,
) : LocalReminderScheduler {

    override suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule> {
        val nowMs = KxClock.System.now().toEpochMilliseconds()
        val delayMs = reminder.deliverAt.toEpochMilliseconds() - nowMs
        if (delayMs <= 0) return Result.failure(NotificationError.Schedule.Failed)
        val req = OneTimeWorkRequestBuilder<StreakNudgeWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(StreakNudgeWorker.KEY_TITLE, reminder.title)
                    .putString(StreakNudgeWorker.KEY_BODY, reminder.body)
                    .build(),
            )
            .addTag(reminder.id)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, req)
        return Result.success(Unit)
    }

    override suspend fun cancel(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(reminderId)
    }
}
