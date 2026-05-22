@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationError
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.jetbrains.compose.resources.getString

/**
 * Adapter that exposes [ScheduleDailyInactivityReminderUseCase] through the cross-feature
 * [StreakNotificationPort]. The port name is kept for backward compat with callers in
 * :feature:meal (PublishMealViewModel et al.); semantics now: schedule the daily 14:00
 * inactivity reminder that fires unless the user has already posted today.
 *
 * The try/catch wraps the entire body so unit tests without bundled Compose Resources still
 * return [Result.failure] rather than crashing the publish flow.
 */
class StreakNotificationAdapter(
    private val useCase: ScheduleDailyInactivityReminderUseCase,
) : StreakNotificationPort {
    override suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError> {
        return try {
            val title = getString(NotificationStringKey.InactivityTitle.resourceId)
            val body  = getString(NotificationStringKey.InactivityBody.resourceId)
            when (useCase(title = title, body = body)) {
                is Result.Ok  -> Result.success(Unit)
                is Result.Err -> Result.failure(StreakNotificationError.Unavailable)
            }
        } catch (t: Throwable) {
            Result.failure(StreakNotificationError.Unavailable)
        }
    }
}
