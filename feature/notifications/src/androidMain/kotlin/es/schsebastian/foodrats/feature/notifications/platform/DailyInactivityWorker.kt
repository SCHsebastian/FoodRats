package es.schsebastian.foodrats.feature.notifications.platform

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

        NotificationChannels.ensureExists(applicationContext)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        // A status-bar small icon MUST be a monochrome silhouette; the app launcher icon renders
        // as a white square. The drawable lives in :androidApp (merged into the app), so resolve it
        // by name at runtime rather than taking a compile-time dependency on :androidApp's R. Fall
        // back to the launcher icon if absent so a resource rename can never crash the worker.
        val resources = applicationContext.resources
        val pkg = applicationContext.packageName
        val smallIcon = resources.getIdentifier("ic_stat_notification", "drawable", pkg)
            .takeIf { it != 0 } ?: applicationContext.applicationInfo.icon
        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.NUDGE_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(smallIcon)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
        resources.getIdentifier("notification_accent", "color", pkg)
            .takeIf { it != 0 }
            ?.let { builder.setColor(ContextCompat.getColor(applicationContext, it)) }
        applicationContext.getSystemService<NotificationManager>()
            ?.notify(NOTIF_ID, builder.build())
        return Result.success()
    }

    /**
     * A streak nudge is "just a reminder" — tapping it should open the app, which lands on Feed
     * (the authenticated start destination). We launch via the package's launcher intent rather
     * than a deep link, so there's no URL contract to keep in sync and no cross-module dependency
     * on `:androidApp`'s MainActivity. Returns null if the launcher intent can't be resolved
     * (then the notification is simply non-tappable, as before).
     */
    private fun openAppIntent(): PendingIntent? {
        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            NOTIF_ID,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val NOTIF_ID = 42
    }
}
