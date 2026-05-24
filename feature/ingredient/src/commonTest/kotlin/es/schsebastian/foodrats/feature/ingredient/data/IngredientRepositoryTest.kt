package es.schsebastian.foodrats.feature.ingredient.data

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.meal.IngredientSlug
import es.schsebastian.foodrats.feature.ingredient.data.firebase.DishIngredientMapDto
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDataSource
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientDto
import es.schsebastian.foodrats.feature.ingredient.data.firebase.IngredientRepository
import es.schsebastian.foodrats.feature.ingredient.data.local.CatalogCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)

class IngredientRepositoryTest {

    @Test
    fun observeCatalog_emits_live_snapshot() = runTest {
        // replay=1 so the emission is buffered before the stateIn collector subscribes
        val liveFlow = MutableSharedFlow<List<IngredientDto>>(replay = 1)
        liveFlow.emit(listOf(IngredientDto("tomato", mapOf("en" to "Tomato"), "Vegetable")))
        val repo = repoWith(live = liveFlow)
        repo.observeCatalog().test {
            // Consume initial emptyMap if present, then find the non-empty item
            var snapshot = awaitItem()
            if (snapshot.isEmpty()) snapshot = awaitItem()
            assertEquals(setOf(IngredientSlug("tomato")), snapshot.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeCatalog_skips_dto_with_blank_slug() = runTest {
        val liveFlow = MutableSharedFlow<List<IngredientDto>>(replay = 1)
        liveFlow.emit(
            listOf(
                IngredientDto("", mapOf("en" to "Invalid"), "Vegetable"),
                IngredientDto("carrot", mapOf("en" to "Carrot"), "Vegetable"),
            ),
        )
        val repo = repoWith(live = liveFlow)
        repo.observeCatalog().test {
            var result = awaitItem()
            if (result.isEmpty()) result = awaitItem()
            assertEquals(setOf(IngredientSlug("carrot")), result.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun suggestForDish_returns_default_ingredients() = runTest {
        val repo = repoWith(dishMap = mapOf("lasagna" to listOf("pasta", "tomato")))
        val slugs = repo.suggestForDish("lasagna")
        assertEquals(listOf(IngredientSlug("pasta"), IngredientSlug("tomato")), slugs)
    }

    @Test
    fun suggestForDish_returns_empty_for_unknown_dish() = runTest {
        val repo = repoWith()
        assertEquals(emptyList(), repo.suggestForDish("ramen"))
    }

    @Test
    fun suggestForDish_skips_blank_slug_entries() = runTest {
        val repo = repoWith(dishMap = mapOf("pizza" to listOf("dough", "", "cheese")))
        val slugs = repo.suggestForDish("pizza")
        assertEquals(listOf(IngredientSlug("dough"), IngredientSlug("cheese")), slugs)
    }

    @Test
    fun findBySlugs_returns_matching_ingredients_from_catalog() = runTest {
        val liveFlow = MutableSharedFlow<List<IngredientDto>>(replay = 1)
        liveFlow.emit(
            listOf(
                IngredientDto("tomato", mapOf("en" to "Tomato"), "Vegetable"),
                IngredientDto("onion", mapOf("en" to "Onion"), "Vegetable"),
            ),
        )
        val repo = repoWith(live = liveFlow)
        // Allow the stateIn to collect the live emission
        repo.observeCatalog().test {
            // consume until we have both slugs
            var snap = awaitItem()
            while (snap.size < 2) snap = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        val result = repo.findBySlugs(setOf(IngredientSlug("tomato")))
        assertEquals(1, result.size)
        assertEquals(IngredientSlug("tomato"), result.first().slug)
    }

    // ---- helpers ----

    private fun TestScope.repoWith(
        live: MutableSharedFlow<List<IngredientDto>> = MutableSharedFlow(replay = 0),
        dishMap: Map<String, List<String>> = emptyMap(),
    ): IngredientRepository {
        val ds = object : IngredientDataSource {
            override fun observeCatalog(): Flow<List<IngredientDto>> = live
            override suspend fun loadDishMap(dishSlug: String): DishIngredientMapDto? =
                dishMap[dishSlug]?.let { DishIngredientMapDto(dishSlug, dishSlug, it) }
        }
        val cache = object : CatalogCache {
            override fun observe(): Flow<List<IngredientDto>> = flowOf(emptyList())
            override suspend fun save(catalog: List<IngredientDto>) { /* no-op */ }
        }
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        return IngredientRepository(ds, cache, dispatchers, { "en" }, backgroundScope)
    }
}
