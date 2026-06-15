package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The single [CuisineReadPort]. Mirrors [IngredientRepository]: the catalog is a [StateFlow] that
 * re-maps display names whenever EITHER the Firestore source OR the active language changes (so an
 * in-app En/Es switch updates names live), kept warm over an app-lifetime [scope]. The catalog
 * order from Firestore defines the passport grid's cell order.
 *
 * No disk cache (unlike ingredients): the catalog is a fixed 14-row set behind the warm app-scope
 * listener, so a `LinkedHashMap` snapshot in memory is enough — the grid never needs it pre-cold-start.
 *
 * One [withContext]`(dispatchers.io)` per public method: only [loadDishCuisine] does I/O ([observeCatalog]
 * returns the already-running snapshot flow). Vendor faults from the listener surface as an empty
 * catalog (the grid renders all-locked, never crashes); a dish lookup fault is folded to `null`
 * (advisory — a failed lookup must NOT block publish, the cuisine just stays unstamped).
 */
class CuisineRepository(
    private val datasource: CuisineDataSource,
    private val dispatchers: DispatcherProvider,
    private val language: Flow<String>,
    private val scope: CoroutineScope,
) : CuisineReadPort {

    private val catalog: StateFlow<Map<CuisineSlug, Cuisine>> =
        combine(
            datasource.observeCatalog(),
            language.distinctUntilChanged(),
        ) { list, lang ->
            // LinkedHashMap (associateBy preserves insertion order) → stable grid order.
            list.mapNotNull { it.toDomain(lang) }.associateBy { it.slug }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    override fun observeCatalog(): Flow<Map<CuisineSlug, Cuisine>> = catalog

    override suspend fun loadDishCuisine(dishSlug: String): CuisineSlug? =
        withContext(dispatchers.io) {
            runCatching { datasource.loadDishCuisine(dishSlug) }
                .getOrNull()
                ?.cuisine
                ?.let { CuisineSlug.of(it).getOrNull() }
        }
}
