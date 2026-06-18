package es.schsebastian.foodrats.core.domain.preferences

import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalTime

/**
 * The user-chosen local times at which the daily "post your meal" reminder fires. Replaces the old
 * hardcoded single 14:00 reminder: the user can pick any time and have up to [MAX_REMINDERS] of them.
 *
 * Persisted locally. The set is the single source of truth: the scheduler in :feature:notifications
 * observes [times] (plus the notifications opt-in) and (re)registers the OS alarms reactively, so a
 * caller only has to [set] — it never schedules directly.
 */
interface MealReminderSchedulePort {
    val times: Flow<List<LocalTime>>

    /** Persists [times] (already de-duplicated/clamped by the caller; the impl also clamps to [MAX_REMINDERS]). */
    suspend fun set(times: List<LocalTime>): Result<Unit, MealReminderPreferenceError>

    companion object {
        const val MAX_REMINDERS = 3

        /** The reminder users had before this was configurable: a single 14:00 nudge. */
        val DEFAULT_TIMES: List<LocalTime> = listOf(LocalTime(hour = 14, minute = 0))
    }
}

sealed interface MealReminderPreferenceError {
    sealed interface Persist : MealReminderPreferenceError {
        data object Unavailable : Persist
    }
}
