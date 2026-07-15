package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable

/** iOS has no notification-channel concept — nothing to sync. */
@Composable
internal actual fun SyncNotificationChannelName() = Unit
