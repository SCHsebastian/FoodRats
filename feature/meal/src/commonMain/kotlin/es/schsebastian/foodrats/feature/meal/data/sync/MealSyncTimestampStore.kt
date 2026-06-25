package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Durable store for per-crew last-synced timestamps (L3).
 *
 * Serialises a `Map<String, Instant>` (crewId → epoch-millis) as a pipe-delimited string
 * (`"crewId1=epochMs1|crewId2=epochMs2"`) so a single DataStore string key holds all crews.
 *
 * Owns the single [withContext] IO boundary: the engine calls [load] and [save] as
 * suspend functions; no withContext inside the engine itself.
 */
internal class MealSyncTimestampStore(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : SyncTimestampPort {

    /** Loads the persisted map (crewId → [Instant]). Returns empty map if nothing stored yet. */
    override suspend fun load(): Map<String, Instant> = withContext(dispatchers.io) {
        val raw = prefs.observe(Keys.MealSyncTimestamps).firstOrNull() ?: return@withContext emptyMap()
        parse(raw)
    }

    /** Persists [stamps] (crewId → [Instant]) durably. */
    override suspend fun save(stamps: Map<String, Instant>): Unit = withContext(dispatchers.io) {
        prefs.set(Keys.MealSyncTimestamps, serialise(stamps))
    }

    companion object {
        internal fun serialise(stamps: Map<String, Instant>): String =
            stamps.entries.joinToString("|") { (id, ts) -> "$id=${ts.toEpochMilliseconds()}" }

        internal fun parse(raw: String): Map<String, Instant> {
            if (raw.isBlank()) return emptyMap()
            return raw.split("|").mapNotNull { entry ->
                val eq = entry.indexOf('=')
                if (eq < 1) return@mapNotNull null
                val id = entry.substring(0, eq)
                val ms = entry.substring(eq + 1).toLongOrNull() ?: return@mapNotNull null
                id to Instant.fromEpochMilliseconds(ms)
            }.toMap()
        }
    }
}
