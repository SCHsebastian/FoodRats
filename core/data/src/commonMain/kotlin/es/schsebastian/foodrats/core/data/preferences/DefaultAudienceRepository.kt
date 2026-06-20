package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudienceError
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudiencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val SEPARATOR = "|"

class DefaultAudienceRepository(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
) : DefaultAudiencePort {

    /**
     * Absent key emits `null` (no preference saved yet).
     * A blank stored value is treated as absent so stale empty-string writes are safe.
     */
    override val defaultAudience: Flow<Set<CrewId>?> =
        prefs.observe(Keys.DefaultAudienceCrewIds).map { raw ->
            if (raw.isNullOrBlank()) null
            else raw.split(SEPARATOR)
                .filter { it.isNotBlank() }
                .mapNotNull { CrewId.of(it).getOrNull() }
                .toSet()
                .takeIf { it.isNotEmpty() }
        }

    override suspend fun set(crewIds: Set<CrewId>): Result<Unit, DefaultAudienceError> =
        withContext(dispatchers.io) {
            runCatching {
                val encoded = crewIds.joinToString(SEPARATOR) { it.value }
                prefs.set(Keys.DefaultAudienceCrewIds, encoded)
            }.fold(
                onSuccess = { Result.success(Unit) },
                onFailure = { Result.failure(DefaultAudienceError.Persist.Unavailable) },
            )
        }
}
