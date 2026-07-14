package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.MealReminderPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort.Companion.MAX_REMINDERS
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime

/**
 * DataStore-backed persistence for the meal-reminder times. Stored as a comma-separated `HH:mm`
 * string (see [Keys.MealReminderTimes]); absent reads as [MealReminderSchedulePort.DEFAULT_TIMES].
 */
class MealReminderScheduleRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : MealReminderSchedulePort {

    override val times: Flow<List<LocalTime>> =
        prefs.observe(Keys.MealReminderTimes).map { stored -> decode(stored) }

    override suspend fun set(times: List<LocalTime>): Result<Unit, MealReminderPreferenceError> =
        withContext(dispatchers.io) {
            // Normalize before persisting: distinct, chronological, clamped to the max.
            val normalized = times.distinct().sortedWith(compareBy({ it.hour }, { it.minute })).take(MAX_REMINDERS)
            persistResult({ MealReminderPreferenceError.Persist.Unavailable }) {
                prefs.set(Keys.MealReminderTimes, encode(normalized))
            }
        }

    private fun encode(times: List<LocalTime>): String =
        // LocalTime.toString() is ISO `HH:mm` (or `HH:mm:ss` if seconds present, which we never set).
        times.joinToString(separator = ",") { it.toString() }

    private fun decode(stored: String?): List<LocalTime> {
        if (stored == null) return MealReminderSchedulePort.DEFAULT_TIMES
        // An explicit empty string means "the user removed all reminders" — distinct from absent.
        if (stored.isBlank()) return emptyList()
        return stored.split(",")
            .mapNotNull { token -> runCatching { LocalTime.parse(token.trim()) }.getOrNull() }
            .distinct()
            .sortedWith(compareBy({ it.hour }, { it.minute }))
            .take(MAX_REMINDERS)
    }
}
