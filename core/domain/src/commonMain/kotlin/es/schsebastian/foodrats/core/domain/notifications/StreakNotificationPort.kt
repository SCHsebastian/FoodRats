package es.schsebastian.foodrats.core.domain.notifications

import es.schsebastian.foodrats.core.domain.result.Result

/**
 * Port for scheduling a local "your streak is at risk" nudge.
 *
 * Title/body are intentionally NOT in the signature — the adapter in :feature:notifications
 * owns the i18n resource lookup, keeping :core:domain free of presentation strings.
 *
 * Declared here so :feature:meal can call it after a publish without depending on
 * :feature:notifications directly.
 */
interface StreakNotificationPort {
    /** Schedules a local "your streak is at risk" nudge for the next delivery window. */
    suspend fun scheduleStreakNudge(): Result<Unit, StreakNotificationError>
}

sealed interface StreakNotificationError {
    data object Unavailable : StreakNotificationError
}
