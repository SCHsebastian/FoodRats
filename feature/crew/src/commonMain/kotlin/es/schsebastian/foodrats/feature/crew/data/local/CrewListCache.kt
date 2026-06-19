package es.schsebastian.foodrats.feature.crew.data.local

// BRIDGE: replaced by SQLDelight in offline-first P3.
//
// Persists the last-seen crew list as a JSON array of CrewDto in DataStore so the
// crew picker survives offline. Mirrors :feature:ingredient's IngredientCatalogCache.
// Disposable once the SQLDelight read source-of-truth (offline-first P3) lands.

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface CrewListCache {
    /** Emits the cached crew list, or `null` while no snapshot has been persisted yet. */
    fun observe(): Flow<List<CrewDto>?>
    suspend fun save(crews: List<CrewDto>)
}

class DataStoreCrewListCache(
    private val prefs: AppPreferences,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : CrewListCache {
    private val serializer = ListSerializer(CrewDto.serializer())

    override fun observe(): Flow<List<CrewDto>?> =
        prefs.observe(Keys.CrewListJson).map { raw ->
            raw?.let { json.decodeFromString(serializer, it) }
        }

    override suspend fun save(crews: List<CrewDto>): Unit = withContext(dispatchers.io) {
        prefs.set(Keys.CrewListJson, json.encodeToString(serializer, crews))
    }
}
