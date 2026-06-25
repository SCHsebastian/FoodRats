package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val SEPARATOR = "|"

/**
 * Persists per-crew welcome-banner dismissals in DataStore. The key
 * [Keys.DismissedWelcomes] holds a pipe-delimited set of crew IDs whose banner the user
 * has already dismissed — matching the pattern used by [DefaultAudienceRepository].
 *
 * Consumed by the `CrewWelcomePort` binding in `crewModule`, which combines these reads/writes
 * with the crew's own live welcome-message string from `CrewRepository`.
 */
class WelcomeDismissalRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) {
    /** Emits the set of crew IDs whose welcome banner has been dismissed. */
    fun observeDismissed(): Flow<Set<String>> =
        prefs.observe(Keys.DismissedWelcomes).map { raw ->
            if (raw.isNullOrBlank()) emptySet()
            else raw.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
        }

    /** Marks the given crew's welcome banner as dismissed. Errors are silently dropped. */
    suspend fun dismiss(crewId: CrewId) {
        withContext(dispatchers.io) {
            runCatching {
                val raw = prefs.observe(Keys.DismissedWelcomes).first()
                val set = (raw?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()).toMutableSet()
                set += crewId.value
                prefs.set(Keys.DismissedWelcomes, set.joinToString(SEPARATOR))
            }
            // Dismissal errors are silently dropped — a failed dismiss just re-shows the banner.
        }
    }
}
