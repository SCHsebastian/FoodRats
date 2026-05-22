package es.schsebastian.foodrats.feature.notifications.platform

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import es.schsebastian.foodrats.core.domain.meal.HasPostedTodayPort
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.result.Result as DomainResult
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Daily 14:00-local periodic worker. Before firing, queries [HasPostedTodayPort] for the user's
 * active crew. If the user has already posted today, the notification is suppressed; otherwise
 * it fires. Errors from the check are treated as "fire anyway" — false negatives are preferable
 * to silence.
 */
class DailyInactivityWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val hasPostedToday: HasPostedTodayPort by inject()
    private val sessionProvider: SessionProvider by inject()
    private val clock: Clock by inject()
    private val zone: TimeZone by inject()

    override suspend fun doWork(): Result {
        val session = sessionProvider.current.firstOrNull()
        val accountId = session?.accountId
        val crewId = session?.activeCrewId
        if (accountId == null || crewId == null) {
            return Result.success()
        }
        val today = MealDay.today(clock, zone)
        val shouldFire = when (val check = hasPostedToday.hasPosted(accountId, crewId, today)) {
            is DomainResult.Ok -> !check.value
            is DomainResult.Err -> true
        }
        if (!shouldFire) return Result.success()

        NotificationChannels.ensure(applicationContext)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
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
