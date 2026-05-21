@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationError
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.jetbrains.compose.resources.getString

/**
 * Adapter that exposes [ScheduleStreakNudgeUseCase] through the cross-feature
 * [StreakNotificationPort]. Owns the Compose-Resources i18n lookup for the nudge
 * title/body that used to live inline in PublishMealViewModel — keeping presentation
 * concerns out of the meal feature's ViewModel.
 *
 * The try/catch wraps the entire body so unit tests without bundled Compose Resources
 * still return [Result.failure] rather than crashing the publish flow.
 */
class StreakNotificationAdapter(
    private val useCase: ScheduleStreakNudgeUseCase,
) : StreakNotificationPort {
    override suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError> {
        return try {
            val title = getString(NotificationStringKey.StreakTitle.resourceId)
            val body  = getString(NotificationStringKey.StreakBody.resourceId)
            when (useCase(title = title, body = body)) {
                is Result.Ok  -> Result.success(Unit)
                is Result.Err -> Result.failure(StreakNotificationError.Unavailable)
            }
        } catch (t: Throwable) {
            // Compose Resources unavailable (e.g., unit-test environments without
            // bundled resources). Mirrors the previous inline behavior in
            // PublishMealViewModel — fail silently so publish stays green.
            Result.failure(StreakNotificationError.Unavailable)
        }
    }
}
