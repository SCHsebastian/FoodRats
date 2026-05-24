package es.schsebastian.foodrats.feature.ingredient.data.local

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class IngredientCatalogCache(
    private val prefs: AppPreferences,
    private val json: Json = Json,
) {
    private val serializer = ListSerializer(IngredientDto.serializer())

    fun observe(): Flow<List<IngredientDto>> = prefs.observe(Keys.IngredientCatalogJson).map { raw ->
        raw?.let { json.decodeFromString(serializer, it) } ?: emptyList()
    }

    suspend fun save(catalog: List<IngredientDto>) {
        prefs.set(Keys.IngredientCatalogJson, json.encodeToString(serializer, catalog))
    }
}
