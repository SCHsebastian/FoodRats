package es.schsebastian.foodrats.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.notifications.data.adapter.MealReminderScheduler
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.koin.compose.koinInject

/**
 * Feeds the localized meal-reminder title/body into [MealReminderScheduler], which schedules
 * nothing until the first copy arrives. Must be called INSIDE [ProvideAppLocale]'s content —
 * `resolve` there reads the applied in-app locale, and the re-keyed subtree recomposes on a
 * language change, so the effect re-fires with translated copy and the scheduler replaces the
 * WorkManager slots with the new text (same principle as [SyncNotificationChannelName]).
 */
@Composable
internal fun SyncMealReminderCopy() {
    val scheduler = koinInject<MealReminderScheduler>()
    val title = resolve(NotificationStringKey.InactivityTitle)
    val body = resolve(NotificationStringKey.InactivityBody)
    LaunchedEffect(title, body) {
        scheduler.onCopyResolved(title = title, body = body)
    }
}
