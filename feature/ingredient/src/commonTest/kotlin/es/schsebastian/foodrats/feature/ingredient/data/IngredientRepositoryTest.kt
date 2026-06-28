package es.schsebastian.foodrats.feature.ingredient.data

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
import es.schsebastian.foodrats.feature.ingredient.data.firebase.DishIngredientMapDto
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDto
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientRepository
import es.schsebastian.foodrats.feature.ingredient.data.local.CatalogCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)

class IngredientRepositoryTest {

    @Test
    fun observeCatalog_emits_seeded_catalog() = runTest {
        // FIREST-4: subscribing triggers the one-shot get() → cache hydration → public flow.
        val repo = repoWith(catalog = listOf(IngredientDto("tomato", mapOf("en" to "Tomato"), "Vegetable")))
        repo.observeCatalog().test {
            var snapshot = awaitItem()
            while (snapshot.isEmpty()) snapshot = awaitItem()
            assertEquals(setOf(IngredientSlug.of("tomato").getOrNull()!!), snapshot.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeCatalog_skips_dto_with_blank_slug() = runTest {
        val repo = repoWith(
            catalog = listOf(
                IngredientDto("", mapOf("en" to "Invalid"), "Vegetable"),
                IngredientDto("carrot", mapOf("en" to "Carrot"), "Vegetable"),
            ),
        )
        repo.observeCatalog().test {
            var result = awaitItem()
            while (result.isEmpty()) result = awaitItem()
            assertEquals(setOf(IngredientSlug.of("carrot").getOrNull()!!), result.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun suggestForDish_returns_default_ingredients() = runTest {
        val repo = repoWith(dishMap = mapOf("lasagna" to listOf("pasta", "tomato")))
        val slugs = repo.suggestForDish("lasagna")
        assertEquals(listOf(IngredientSlug.of("pasta").getOrNull()!!, IngredientSlug.of("tomato").getOrNull()!!), slugs)
    }

    @Test
    fun suggestForDish_returns_empty_for_unknown_dish() = runTest {
        val repo = repoWith()
        assertEquals(emptyList(), repo.suggestForDish("ramen"))
    }

    // ingredient-01: Firestore throw must degrade to empty list, not propagate
    @Test
    fun suggestForDish_returns_empty_when_datasource_throws() = runTest {
        val throwingDs = object : IngredientDataSource {
            override suspend fun loadCatalog(): List<IngredientDto> = emptyList()
            override suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto? =
                throw RuntimeException("network error")
        }
        val cache = InMemoryCatalogCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        val repo = IngredientRepository(throwingDs, cache, dispatchers, flowOf("en"), backgroundScope)
        assertEquals(emptyList(), repo.suggestForDish("sushi"))
    }

    @Test
    fun suggestForDish_skips_blank_slug_entries() = runTest {
        val repo = repoWith(dishMap = mapOf("pizza" to listOf("dough", "", "cheese")))
        val slugs = repo.suggestForDish("pizza")
        assertEquals(listOf(IngredientSlug.of("dough").getOrNull()!!, IngredientSlug.of("cheese").getOrNull()!!), slugs)
    }

    @Test
    fun findBySlugs_returns_matching_ingredients_from_catalog() = runTest {
        val repo = repoWith(
            catalog = listOf(
                IngredientDto("tomato", mapOf("en" to "Tomato"), "Vegetable"),
                IngredientDto("onion", mapOf("en" to "Onion"), "Vegetable"),
            ),
        )
        // Subscribe so the one-shot hydration runs and the catalog warms.
        repo.observeCatalog().test {
            var snap = awaitItem()
            while (snap.size < 2) snap = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val result = repo.findBySlugs(setOf(IngredientSlug.of("tomato").getOrNull()!!))
        assertEquals(1, result.size)
        assertEquals(IngredientSlug.of("tomato").getOrNull()!!, result.first().slug)
    }

    // ---- helpers ----

    private class InMemoryCatalogCache : CatalogCache {
        private val state = MutableStateFlow<List<IngredientDto>>(emptyList())
        override fun observe(): Flow<List<IngredientDto>> = state
        override suspend fun save(catalog: List<IngredientDto>) {
            state.value = catalog
        }
    }

    private fun TestScope.repoWith(
        catalog: List<IngredientDto> = emptyList(),
        dishMap: Map<String, List<String>> = emptyMap(),
    ): IngredientRepository {
        val ds = object : IngredientDataSource {
            override suspend fun loadCatalog(): List<IngredientDto> = catalog
            override suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto? =
                dishMap[dishSlug]?.let { DishIngredientMapDto(dishSlug, dishSlug, it) }
        }
        val cache = InMemoryCatalogCache()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        return IngredientRepository(ds, cache, dispatchers, flowOf("en"), backgroundScope)
    }
}
