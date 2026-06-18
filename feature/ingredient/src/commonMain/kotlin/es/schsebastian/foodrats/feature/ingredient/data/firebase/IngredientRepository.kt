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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
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

    // Re-maps display names whenever EITHER the catalog source OR the active
    // language changes, so an in-app En/Es switch updates names live (not just
    // on first load). `language` resolves System -> device language upstream.
    private val catalog: StateFlow<Map<IngredientSlug, Ingredient>> =
        combine(
            merge(
                cache.observe(),
                datasource.observeCatalog()
                    .onEach { latest ->
                        // ingredient-03: don't block the collecting flow on IO; fire-and-forget
                        scope.launch { withContext(dispatchers.io) { cache.save(latest) } }
                    },
            )
                .distinctUntilChanged(), // ingredient-06: suppress double-emit from cache+live
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
}
