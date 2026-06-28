package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.cuisine.Cuisine
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.ingredient.data.local.CuisineCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single [CuisineReadPort]. Mirrors [IngredientRepository]: the catalog is a [StateFlow] that
 * re-maps display names whenever EITHER the cache OR the active language changes (so an in-app En/Es
 * switch updates names live), kept warm over an app-lifetime [scope]. The catalog order (preserved
 * through the cache as an insertion-ordered list) defines the passport grid's cell order.
 *
 * FIREST-4: the catalog is a DataStore disk cache ([CuisineCatalogCache], mirroring the ingredient
 * one) hydrated by a single one-shot Firestore `get()` per app launch ([refreshOnce]) — NOT a warm
 * snapshot listener. A mid-session admin catalog change won't appear live, which is acceptable for a
 * fixed 14-row set; a transient fetch fault leaves the last-good cache serving.
 *
 * One [withContext]`(dispatchers.io)` per public method: only [loadDishCuisine] does I/O ([observeCatalog]
 * returns the cache-backed flow; the one-shot hydration is a private background helper). A failed
 * catalog read surfaces an empty catalog (the grid renders all-locked, never crashes); a dish lookup
 * fault is folded to `null` (advisory — a failed lookup must NOT block publish, the cuisine just stays
 * unstamped).
 */
class CuisineRepository(
    private val datasource: CuisineDataSource,
    private val cache: CuisineCache,
    private val dispatchers: DispatcherProvider,
    private val language: Flow<String>,
    private val scope: CoroutineScope,
) : CuisineReadPort {

    private var refreshed = false

    private val catalog: StateFlow<Map<CuisineSlug, Cuisine>> =
        combine(
            cache.observe()
                .onStart { refreshOnce() }
                .distinctUntilChanged(),
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

    // Hydrate the disk cache from one Firestore read, at most once per app launch (guarded before the
    // fetch). A transient fault leaves the cache untouched. Fire-and-forget on [scope]: must not block
    // the collecting flow.
    private fun refreshOnce() {
        if (refreshed) return
        refreshed = true
        scope.launch {
            runCatching {
                withContext(dispatchers.io) {
                    cache.save(datasource.loadCatalog())
                }
            }
        }
    }
}
