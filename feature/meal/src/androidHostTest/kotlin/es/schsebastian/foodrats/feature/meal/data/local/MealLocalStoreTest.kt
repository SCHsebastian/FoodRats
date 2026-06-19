package es.schsebastian.foodrats.feature.meal.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.feature.meal.data.firebase.MealDto
import es.schsebastian.foodrats.feature.meal.data.firebase.RatingEntryDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host-test (JVM) coverage of [MealLocalStore]: seed via the sync-write methods, then assert the
 * reactive reads project the rows + denormalized ratings into [LocalMeal], and that
 * [MealLocalStore.replaceCrewWindow] deletes-by-absence only within the window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealLocalStoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: MealLocalStore

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            execute(null, "PRAGMA foreign_keys = ON", 0)
            FoodRatsDatabase.Schema.create(this)
        }
        store = MealLocalStore(FoodRatsDatabase(driver), dispatchers)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun dto(
        id: String,
        crewId: String = "c1",
        dayKey: String = "2026-06-19",
        publishedAtEpochMs: Long,
        ratings: Map<String, RatingEntryDto> = emptyMap(),
        ingredients: List<String> = emptyList(),
    ) = MealDto(
        id = id,
        authorId = "author-$id",
        authorName = "Author $id",
        crewId = crewId,
        dayKey = dayKey,
        slot = "lunch",
        platePath = "crews/$crewId/meals/$id.jpg",
        dishName = "Lasagna",
        publishedAtEpochMs = publishedAtEpochMs,
        ratings = ratings,
        ratingSum = ratings.values.sumOf { it.score },
        voterCount = ratings.size,
        ingredients = ingredients,
    )

    @Test fun upsert_all_then_observe_feed_orders_newest_first_with_paths() = runTest {
        store.upsertAll(
            listOf(
                dto("m1", publishedAtEpochMs = 100L),
                dto("m2", publishedAtEpochMs = 300L),
                dto("m3", publishedAtEpochMs = 200L),
                // Different crew/day — must not leak.
                dto("m4", crewId = "c2", publishedAtEpochMs = 999L),
                dto("m5", dayKey = "2026-06-18", publishedAtEpochMs = 999L),
            ),
        )

        store.observeFeed("c1", "2026-06-19").test {
            val rows = awaitItem()
            assertEquals(listOf("m2", "m3", "m1"), rows.map { it.mealId })
            // Storage PATH is stored verbatim — never a signed URL.
            assertEquals("crews/c1/meals/m2.jpg", rows.first().platePath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observe_feed_joins_denormalized_ratings_and_csv_round_trips() = runTest {
        store.upsertAll(
            listOf(
                dto(
                    "m1",
                    publishedAtEpochMs = 100L,
                    ratings = mapOf(
                        "rater-1" to RatingEntryDto(score = 4, atMs = 10L),
                        "rater-2" to RatingEntryDto(score = 5, atMs = 11L),
                    ),
                    ingredients = listOf("cheese", "tomato"),
                ),
            ),
        )

        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()
            assertEquals(2, meal.ratings.size)
            assertEquals(4, meal.ratings.first { it.raterId == "rater-1" }.score)
            // CSV → MealDto reconstruction reuses the existing enrichment path.
            assertEquals("cheese,tomato", meal.ingredientsCsv)
            assertEquals(listOf("cheese", "tomato"), meal.toMealDto().ingredients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun observe_range_is_inclusive_on_both_ends() = runTest {
        store.upsertAll(
            listOf(
                dto("a", dayKey = "2026-06-10", publishedAtEpochMs = 1L),
                dto("b", dayKey = "2026-06-15", publishedAtEpochMs = 2L),
                dto("c", dayKey = "2026-06-20", publishedAtEpochMs = 3L),
                dto("d", dayKey = "2026-06-21", publishedAtEpochMs = 4L),
            ),
        )

        store.observeRange("c1", "2026-06-10", "2026-06-20").test {
            assertEquals(setOf("a", "b", "c"), awaitItem().map { it.mealId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun replace_crew_window_deletes_by_absence_within_window_only() = runTest {
        // Seed: an OLD meal outside the sync window, and two inside it.
        store.upsertAll(
            listOf(
                dto("old", dayKey = "2026-05-01", publishedAtEpochMs = 1L),
                dto("inside-keep", dayKey = "2026-06-15", publishedAtEpochMs = 2L),
                dto("inside-gone", dayKey = "2026-06-16", publishedAtEpochMs = 3L),
            ),
        )

        // Re-sync the window with ONLY inside-keep present → inside-gone deleted, old retained.
        store.replaceCrewWindow(
            crewId = "c1",
            fromKey = "2026-06-10",
            toKey = "2026-06-20",
            dtos = listOf(dto("inside-keep", dayKey = "2026-06-15", publishedAtEpochMs = 2L)),
        )

        store.observeRange("c1", "2026-05-01", "2026-06-20").test {
            assertEquals(setOf("old", "inside-keep"), awaitItem().map { it.mealId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_all_replaces_an_existing_row_in_place() = runTest {
        store.upsertAll(listOf(dto("m1", publishedAtEpochMs = 100L)))
        store.upsertAll(
            listOf(dto("m1", publishedAtEpochMs = 100L).copy(dishName = "Pizza", description = "now cheesy")),
        )

        store.observeFeed("c1", "2026-06-19").test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("Pizza", rows.single().dishName)
            assertEquals("now cheesy", rows.single().description)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
