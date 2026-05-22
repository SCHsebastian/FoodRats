package es.schsebastian.foodrats.feature.notifications.platform

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class StreakNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NotificationChannels.ensure(applicationContext)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body  = inputData.getString(KEY_BODY).orEmpty()
        val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.NUDGE_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService<NotificationManager>()
            ?.notify(NOTIF_ID, notif)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val NOTIF_ID = 42
    }
}
