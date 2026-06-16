package es.schsebastian.foodrats.feature.ingredient.data.firebase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.cuisine.CuisineSlug
import es.schsebastian.foodrats.core.domain.result.getOrNull
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CuisineRepositoryTest {

    @Test
    fun observeCatalog_emits_and_remaps_on_snapshot() = runTest {
        // replay=1 so the emission is buffered before the stateIn collector subscribes
        val liveFlow = MutableSharedFlow<List<CuisineDto>>(replay = 1)
        liveFlow.emit(listOf(CuisineDto("italian", mapOf("en" to "Italian"))))
        val repo = repoWith(live = liveFlow)
        repo.observeCatalog().test {
            // Consume initial emptyMap if present, then find the non-empty item
            var snapshot = awaitItem()
            if (snapshot.isEmpty()) snapshot = awaitItem()
            assertEquals(setOf(CuisineSlug.of("italian").getOrNull()!!), snapshot.keys)
            assertEquals(
                "Italian",
                snapshot.getValue(CuisineSlug.of("italian").getOrNull()!!).displayName,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeCatalog_remaps_display_names_when_language_changes() = runTest {
        val liveFlow = MutableSharedFlow<List<CuisineDto>>(replay = 1)
        liveFlow.emit(
            listOf(CuisineDto("italian", mapOf("en" to "Italian", "es" to "Italiana"))),
        )
        val languageFlow = MutableSharedFlow<String>(replay = 1)
        languageFlow.emit("en")
        val italian = CuisineSlug.of("italian").getOrNull()!!
        val repo = repoWith(live = liveFlow, language = languageFlow)
        repo.observeCatalog().test {
            var snapshot = awaitItem()
            if (snapshot.isEmpty()) snapshot = awaitItem()
            assertEquals("Italian", snapshot.getValue(italian).displayName)

            languageFlow.emit("es")
            assertEquals("Italiana", awaitItem().getValue(italian).displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeCatalog_skips_dto_with_blank_slug() = runTest {
        val liveFlow = MutableSharedFlow<List<CuisineDto>>(replay = 1)
        liveFlow.emit(
            listOf(
                CuisineDto("", mapOf("en" to "Invalid")),
                CuisineDto("japanese", mapOf("en" to "Japanese")),
            ),
        )
        val repo = repoWith(live = liveFlow)
        repo.observeCatalog().test {
            var result = awaitItem()
            if (result.isEmpty()) result = awaitItem()
            assertEquals(setOf(CuisineSlug.of("japanese").getOrNull()!!), result.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadDishCuisine_resolves_mapped_cuisine_slug() = runTest {
        val repo = repoWith(dishMap = mapOf("pizza" to "italian"))
        assertEquals(CuisineSlug.of("italian").getOrNull()!!, repo.loadDishCuisine("pizza"))
    }

    @Test
    fun loadDishCuisine_returns_null_on_datasource_error() = runTest {
        val repo = repoWith(loadThrows = true)
        assertNull(repo.loadDishCuisine("pizza"))
    }

    @Test
    fun loadDishCuisine_returns_null_on_invalid_slug() = runTest {
        // A blank cuisine value fails CuisineSlug.of, folding to null.
        val repo = repoWith(dishMap = mapOf("pizza" to ""))
        assertNull(repo.loadDishCuisine("pizza"))
    }

    // ---- helpers ----

    private fun TestScope.repoWith(
        live: MutableSharedFlow<List<CuisineDto>> = MutableSharedFlow(replay = 0),
        dishMap: Map<String, String> = emptyMap(),
        loadThrows: Boolean = false,
        language: Flow<String> = flowOf("en"),
    ): CuisineRepository {
        val ds = object : CuisineDataSource {
            override fun observeCatalog(): Flow<List<CuisineDto>> = live
            override suspend fun loadDishCuisine(dishSlug: String): DishCuisineMapDto? {
                if (loadThrows) error("datasource fault")
                return dishMap[dishSlug]?.let { DishCuisineMapDto(dishSlug, it) }
            }
        }
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        return CuisineRepository(ds, dispatchers, language, backgroundScope)
    }
}
