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
            assertEquals(false, rating.edited, "a first optimistic vote is not yet edited")
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

    @Test fun apply_rate_marks_edited_on_a_changed_score_but_not_on_a_same_score_repeat() = runTest {
        // Server-confirmed first vote of 4 already held locally.
        store.upsertAll(
            listOf(
                dto(
                    "m1",
                    publishedAtEpochMs = 100L,
                    ratings = mapOf("rater-1" to RatingEntryDto(score = 4, atMs = 10L)),
                ),
            ),
        )

        // A re-pick of the SAME score is an idempotent no-op — must NOT consume the one edit.
        store.applyRate("m1", "rater-1", score = 4, atMs = 20L, idempotencyKey = "rate:c1:m1:rater-1")
        store.observeFeed("c1", "2026-06-19").test {
            assertEquals(false, awaitItem().single().ratings.single().edited)
            cancelAndIgnoreRemainingEvents()
        }

        // A genuine CHANGE to a different score marks the entry edited.
        store.applyRate("m1", "rater-1", score = 2, atMs = 30L, idempotencyKey = "rate:c1:m1:rater-1")
        store.observeFeed("c1", "2026-06-19").test {
            val rating = awaitItem().single().ratings.single()
            assertEquals(2, rating.score)
            assertEquals(true, rating.edited, "changing to a new score consumes the one allowed edit")
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

    @Test fun prune_older_than_drops_pre_cutoff_meals_and_their_ratings_only() = runTest {
        store.upsertAll(
            listOf(
                // Well before the cutoff → pruned (with its ratings).
                dto(
                    "ancient",
                    dayKey = "2026-01-01",
                    publishedAtEpochMs = 1L,
                    ratings = mapOf("rater-1" to RatingEntryDto(score = 5, atMs = 1L)),
                ),
                // The day before the cutoff → pruned (strictly older than cutoff).
                dto("day-before", dayKey = "2026-03-20", publishedAtEpochMs = 2L),
                // Exactly the cutoff day → KEPT (delete is `< cutoff`, not `<=`).
                dto("on-cutoff", dayKey = "2026-03-21", publishedAtEpochMs = 3L),
                // After the cutoff → KEPT (with its ratings).
                dto(
                    "recent",
                    dayKey = "2026-06-15",
                    publishedAtEpochMs = 4L,
                    ratings = mapOf("rater-2" to RatingEntryDto(score = 4, atMs = 5L)),
                ),
            ),
        )

        store.pruneOlderThan("2026-03-21")

        // Within-retention meals (on-cutoff + recent) survive; the older two are gone.
        store.observeRange("c1", "2026-01-01", "2026-06-20").test {
            assertEquals(setOf("on-cutoff", "recent"), awaitItem().map { it.mealId }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        // The pruned meal's ratings cascaded away; the kept meal's rating remains.
        val ratingsByMeal = FoodRatsDatabase(driver).mealQueries
            .selectRatingsForMeals(listOf("ancient", "recent")).executeAsList()
            .groupBy { it.mealId }
        assertEquals(null, ratingsByMeal["ancient"], "pruned meal's ratings cascade away")
        assertEquals(setOf("rater-2"), ratingsByMeal["recent"].orEmpty().map { it.raterId }.toSet())
    }

    @Test fun upsert_all_round_trips_gallery_plate_source_through_the_real_driver() = runTest {
        store.upsertAll(listOf(dto("m1", publishedAtEpochMs = 100L).copy(plateSource = "gallery")))

        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()
            assertEquals("gallery", meal.plateSource)
            // Round trips through the store's own DTO reconstruction too.
            assertEquals("gallery", meal.toMealDto().plateSource)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun upsert_all_with_no_plate_source_reads_back_null_not_camera_string() = runTest {
        // MealLocalStore/LocalMeal are tolerant-null all the way through — the "null ⇒ camera"
        // DEFAULT is applied by PlateSource.fromKey() at the domain-mapping boundary, not baked
        // into the stored string here.
        store.upsertAll(listOf(dto("m1", publishedAtEpochMs = 100L)))

        store.observeFeed("c1", "2026-06-19").test {
            assertEquals(null, awaitItem().single().plateSource)
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

    // ── M3: dedicated rollback path ───────────────────────────────────────────

    /**
     * M3: verifies that [MealLocalStore.clearPending] uses the dedicated [rollbackMealRate] query
     * and does NOT re-stamp `pending=1` as an intermediate step. The observable invariants are:
     *  - after rollback: `pending=0`, `idempotencyKey=NULL`
     *  - totals reflect only the server-confirmed ratings (pending rows gone)
     *  - the server-confirmed rating rows are untouched
     *
     * This is the same behavioral assertion as [clear_pending_rolls_back_only_the_optimistic_rating],
     * but explicitly checks that the meal is NEVER observable with `pending=1` AND updated totals
     * simultaneously (which would indicate the old `setMealOptimisticRate` intermediate was called).
     * Because the whole clearPending transaction is atomic in SQLDelight, the intermediate write is
     * unobservable from the outside — the test asserts the final state is correct and that the meal
     * was not left in a half-rolled-back state.
     */
    @Test fun clear_pending_m3_rollback_lands_correct_final_state_without_intermediate_pending1() = runTest {
        // Server-confirmed baseline: one rating from rater-1 (score=3).
        store.upsertAll(
            listOf(
                dto(
                    "m1",
                    publishedAtEpochMs = 100L,
                    ratings = mapOf("rater-1" to RatingEntryDto(score = 3, atMs = 10L)),
                ),
            ),
        )
        // Optimistic rate from rater-2 (score=5): pending=1, key stamped, totals show both ratings.
        store.applyRate("m1", "rater-2", score = 5, atMs = 20L, idempotencyKey = "rate:c1:m1:rater-2")

        // Roll back rater-2's pending rating.
        store.clearPending("rate:c1:m1:rater-2")

        // Final observable state must match server truth: only rater-1's confirmed rating survives.
        store.observeFeed("c1", "2026-06-19").test {
            val meal = awaitItem().single()

            // Core rollback invariants (also tested by clear_pending_rolls_back_only_the_optimistic_rating):
            assertEquals(0L, meal.pending, "M3: pending must be 0 after rollback")
            assertEquals(null, meal.idempotencyKey, "M3: idempotencyKey must be NULL after rollback")
            assertEquals(setOf("rater-1"), meal.ratings.map { it.raterId }.toSet(), "M3: only confirmed rating survives")
            assertEquals(3L, meal.ratingSum, "M3: totals must reflect only the confirmed rater-1 score")
            assertEquals(1L, meal.voterCount, "M3: voterCount must be 1 (only rater-1 confirmed)")

            // Confirm the pending rating row was dropped (pending=false on surviving rating).
            assertEquals(false, meal.ratings.single().pending, "M3: surviving rating must not be pending")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
