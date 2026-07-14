package es.schsebastian.foodrats.feature.notifications.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val NUDGE_ID = "fr_nudge"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(NUDGE_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(NUDGE_ID, "Streak nudges", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
