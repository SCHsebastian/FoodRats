package es.schsebastian.foodrats.feature.ingredient.data.local

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.feature.ingredient.data.firebase.CuisineDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Disk cache of the static cuisine catalog, mirroring [CatalogCache]/[IngredientCatalogCache] over the
 * same DataStore-backed [AppPreferences]. The repository hydrates it with a single one-shot Firestore
 * read per app launch (FIREST-4) and serves the `CuisineReadPort` flow from here, so there is no warm
 * snapshot listener. Stored order is preserved (insertion-ordered JSON list) → stable passport grid.
 */
interface CuisineCache {
    fun observe(): Flow<List<CuisineDto>>
    suspend fun save(catalog: List<CuisineDto>)
}

class CuisineCatalogCache(
    private val prefs: AppPreferences,
    private val json: Json = Json,
) : CuisineCache {
    private val serializer = ListSerializer(CuisineDto.serializer())

    override fun observe(): Flow<List<CuisineDto>> = prefs.observe(Keys.CuisineCatalogJson).map { raw ->
        raw?.let { json.decodeFromString(serializer, it) } ?: emptyList()
    }

    override suspend fun save(catalog: List<CuisineDto>) {
        prefs.set(Keys.CuisineCatalogJson, json.encodeToString(serializer, catalog))
    }
}
