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

    @Test fun apply_rate_writes_a_pending_row_and_recomputes_totals_then_sync_clears_it() = runTest {
        // A meal already held locally (server-confirmed, no ratings yet).
        store.upsertAll(listOf(dto("m1", publishedAtEpochMs = 100L)))

        // Optimistic offline rate: pending=1 mealRating row + meal pending=1 + idempotencyKey stamped.
        store.applyRate(
            mealId = "m1",
            raterId = "rater-1",
            score = 4,
            atMs = 42L,
            idempotencyKey = "rate:c1:m1:rater-1",
        )

        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()
            assertEquals(1L, meal.pending)
            assertEquals("rate:c1:m1:rater-1", meal.idempotencyKey)
            assertEquals(4L, meal.ratingSum)
            assertEquals(1L, meal.voterCount)
            val rating = meal.ratings.single()
            assertEquals("rater-1", rating.raterId)
            assertEquals(4, rating.score)
            assertEquals(true, rating.pending)
            cancelAndIgnoreRemainingEvents()
        }

        // The rated meal syncs back from the server (confirmed rating) — overwrites the pending row.
        store.upsertAll(
            listOf(
                dto(
                    "m1",
                    publishedAtEpochMs = 100L,
                    ratings = mapOf("rater-1" to RatingEntryDto(score = 4, atMs = 50L)),
                ),
            ),
        )

        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()
            assertEquals(0L, meal.pending, "server snapshot clears the meal pending flag")
            assertEquals(null, meal.idempotencyKey)
            assertEquals(4L, meal.ratingSum)
            assertEquals(1L, meal.voterCount)
            assertEquals(false, meal.ratings.single().pending, "the confirmed rating is no longer pending")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun apply_rate_on_uncached_meal_is_a_noop() = runTest {
        // No meal m-absent held locally → optimistic rate has nothing to render against.
        store.applyRate(
            mealId = "m-absent",
            raterId = "rater-1",
            score = 5,
            atMs = 1L,
            idempotencyKey = "rate:c1:m-absent:rater-1",
        )

        store.observeFeed("c1", "2026-06-19").test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun clear_pending_rolls_back_only_the_optimistic_rating() = runTest {
        // A confirmed rating from rater-1, plus an optimistic pending rate from rater-2.
        store.upsertAll(
            listOf(
                dto(
                    "m1",
                    publishedAtEpochMs = 100L,
                    ratings = mapOf("rater-1" to RatingEntryDto(score = 3, atMs = 10L)),
                ),
            ),
        )
        store.applyRate("m1", "rater-2", score = 5, atMs = 20L, idempotencyKey = "rate:c1:m1:rater-2")

        // Terminal failure → roll back the optimistic write keyed by its idempotency token.
        store.clearPending("rate:c1:m1:rater-2")

        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()
            assertEquals(0L, meal.pending)
            assertEquals(null, meal.idempotencyKey)
            // Only the confirmed rater-1 rating survives; totals recomputed from it alone.
            assertEquals(setOf("rater-1"), meal.ratings.map { it.raterId }.toSet())
            assertEquals(3L, meal.ratingSum)
            assertEquals(1L, meal.voterCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun clear_pending_for_unknown_key_is_a_noop() = runTest {
        store.upsertAll(listOf(dto("m1", publishedAtEpochMs = 100L)))

        store.clearPending("rate:c1:nope:rater-1")

        store.observeFeed("c1", "2026-06-19").test {
            assertEquals(listOf("m1"), awaitItem().map { it.mealId })
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
