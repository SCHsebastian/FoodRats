package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import es.schsebastian.foodrats.feature.notifications.platform.NotificationChannels

@Composable
internal actual fun SyncNotificationChannelName() {
    val context = LocalContext.current.applicationContext
    // Keyed on the resolved language tag so an in-app picker change re-syncs (renames) the channel.
    val tag = LocalAppLocale.current
    LaunchedEffect(tag) {
        NotificationChannels.sync(context)
    }
}
