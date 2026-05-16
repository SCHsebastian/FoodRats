package es.schsebastian.foodrats.feature.notifications.domain.repository

import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder

interface LocalReminderScheduler {
    suspend fun schedule(reminder: Reminder): Result<Unit, NotificationError.Schedule>
    suspend fun cancel(reminderId: String)
}
