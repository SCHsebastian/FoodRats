package es.schsebastian.foodrats.feature.ingredient.data.firebase

import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.Ingredient
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.feature.ingredient.data.local.CatalogCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class IngredientRepository(
    private val datasource: IngredientDataSource,
    private val cache: CatalogCache,
    private val dispatchers: DispatcherProvider,
    private val currentLang: () -> String,
    private val scope: CoroutineScope,
) : IngredientReadPort {

    private val catalog: StateFlow<Map<IngredientSlug, Ingredient>> =
        merge(
            cache.observe(),
            datasource.observeCatalog()
                .onEach { latest ->
                    withContext(dispatchers.io) { cache.save(latest) }
                },
        )
            .map { list -> list.mapNotNull { it.toDomain(currentLang()) }.associateBy { it.slug } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    override fun observeCatalog(): Flow<Map<IngredientSlug, Ingredient>> = catalog

    override suspend fun findBySlugs(slugs: Set<IngredientSlug>): List<Ingredient> =
        catalog.value.filterKeys { it in slugs }.values.toList()

    override suspend fun suggestForDish(dishSlug: String): List<IngredientSlug> =
        withContext(dispatchers.io) {
            datasource.loadDishMap(dishSlug)?.defaultIngredients
                ?.mapNotNull { runCatching { IngredientSlug(it) }.getOrNull() }
                ?: emptyList()
        }
}
