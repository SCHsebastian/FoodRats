package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.ingredient.data.local.CatalogCache
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

class IngredientRepository(
    private val datasource: IngredientDataSource,
    private val cache: CatalogCache,
    private val dispatchers: DispatcherProvider,
    private val language: Flow<String>,
    private val scope: CoroutineScope,
) : IngredientReadPort {

    // FIREST-4: the static, admin-seeded catalog no longer rides a warm Firestore snapshot listener.
    // The public StateFlow serves from the DataStore cache; a single one-shot get() per app launch
    // ([refreshOnce]) hydrates that cache. A mid-session admin catalog change won't appear live — fine
    // for a static catalog. Re-maps display names whenever EITHER the cache OR the active language
    // changes, so an in-app En/Es switch updates names live. `language` resolves System -> device.
    private var refreshed = false

    private val catalog: StateFlow<Map<IngredientSlug, Ingredient>> =
        combine(
            cache.observe()
                .onStart { refreshOnce() }
                .distinctUntilChanged(),
            language.distinctUntilChanged(),
        ) { list, lang -> list.mapNotNull { it.toDomain(lang) }.associateBy { it.slug } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = catalog

    override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> =
        catalog.value.filterKeys { it in slugs }.values.toList()

    // ingredient-01: catalog miss or transient Firestore throw degrades to no suggestions
    override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> =
        withContext(dispatchers.io) {
            runCatching { datasource.loadDishMap(dishSlug) }
                .getOrNull()
                ?.defaultIngredients
                ?.mapNotNull { IngredientSlug.of(it).getOrNull() }
                ?: emptyList()
        }

    // ingredient-03: hydrate the disk cache from one Firestore read, at most once per app launch
    // (guarded before the fetch so flapping subscriptions can't refetch). A transient fault leaves
    // the cache untouched, so the last-good snapshot keeps serving. Fire-and-forget on [scope]:
    // it must not block the collecting flow.
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
