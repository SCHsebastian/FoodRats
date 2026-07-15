package es.schsebastian.foodrats.feature.notifications.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString

object NotificationChannels {
    const val NUDGE_ID = "fr_nudge"

    /**
     * Creates (or renames) the streak-nudge channel. `createNotificationChannel` is called
     * UNCONDITIONALLY (no `getNotificationChannel(...) == null` guard) — re-creating with the same
     * [NUDGE_ID] is a no-op for existing settings but DOES update the user-visible name, which is
     * how the label follows the in-app language live. This looks redundant but is load-bearing;
     * don't reintroduce the guard.
     *
     * Call ONLY from composition (`SyncNotificationChannelName` in `:shared`), i.e. strictly after
     * `LocalAppLocale.provides` has applied the in-app override to the default locale in the same
     * frame — that's the only point where `getString` resolves against the user's chosen language.
     * Background code must use [ensureExists] instead: its process default locale is the DEVICE
     * locale, and an unconditional create there would flap an in-app-language name back to the
     * device language.
     */
    suspend fun sync(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        create(mgr)
    }

    /**
     * Creates the streak-nudge channel only if it doesn't exist yet — the defensive pre-notify
     * guarantee for background paths ([DailyInactivityWorker]), since a notify on a missing channel
     * is silently dropped on O+. Never renames (see [sync] for why background code must not).
     */
    suspend fun ensureExists(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(NUDGE_ID) == null) create(mgr)
    }

    /**
     * The channel name is resolved off any Composable context, mirroring
     * [es.schsebastian.foodrats.feature.notifications.data.adapter.MealReminderScheduler]; a
     * missing-resources environment (unit tests, very early boot) degrades to the English literal
     * instead of crashing — the channel must always exist before a notify.
     */
    @OptIn(ExperimentalResourceApi::class)
    private suspend fun create(mgr: NotificationManager) {
        val name = try {
            getString(NotificationStringKey.ChannelStreakNudges.resourceId)
        } catch (t: Throwable) {
            FrLog.w(FrLog.Tags.Notifications) { "[NotificationChannels] could not resolve channel name: ${t.message}" }
            "Streak nudges"
        }
        mgr.createNotificationChannel(
            NotificationChannel(NUDGE_ID, name, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
