package es.schsebastian.foodrats.core.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-platform behavior of the meal/mealRating schema. Runs on the host JVM
 * (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`) via the
 * expect/actual [createInMemorySqlDriver].
 */
class MealDatabaseTest {
    private lateinit var db: FoodRatsDatabase

    @BeforeTest fun setUp() {
        db = FoodRatsDatabase(createInMemorySqlDriver())
    }

    @AfterTest fun tearDown() {
        // Driver is closed by the GC of the in-memory connection; nothing persistent to clean.
    }

    private fun insertMeal(
        mealId: String,
        crewId: String,
        dayKey: String,
        publishedAtEpochMs: Long,
        pending: Long = 0L,
    ) = db.mealQueries.upsertMeal(
        mealId = mealId,
        crewId = crewId,
        authorId = "author-$mealId",
        authorName = "Author $mealId",
        dayKey = dayKey,
        slot = "lunch",
        platePath = "crews/$crewId/meals/$mealId.jpg",
        thumbnailPath = null,
        thumbHash = null,
        dishName = "Lasagna",
        description = "",
        latitude = null,
        longitude = null,
        publishedAtEpochMs = publishedAtEpochMs,
        ratingSum = 0L,
        voterCount = 0L,
        ingredientsCsv = "",
        classifierVersion = null,
        cuisine = null,
        kind = "solo",
        plateSource = null,
        pending = pending,
        idempotencyKey = null,
    )

    @Test fun upsert_then_select_feed_by_crew_day_orders_newest_first() {
        insertMeal("m1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 100L)
        insertMeal("m2", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 300L)
        insertMeal("m3", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 200L)
        // Different crew / day — must not leak into the query.
        insertMeal("m4", crewId = "c2", dayKey = "2026-06-19", publishedAtEpochMs = 999L)
        insertMeal("m5", crewId = "c1", dayKey = "2026-06-18", publishedAtEpochMs = 999L)

        val rows = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList()

        assertEquals(listOf("m2", "m3", "m1"), rows.map { it.mealId })
    }

    @Test fun upsert_is_insert_or_replace() {
        insertMeal("m1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 100L)
        // Re-upsert same PK with a new dish name + rating fields.
        db.mealQueries.upsertMeal(
            mealId = "m1",
            crewId = "c1",
            authorId = "author-m1",
            authorName = "Author m1",
            dayKey = "2026-06-19",
            slot = "dinner",
            platePath = null,
            thumbnailPath = null,
            thumbHash = null,
            dishName = "Pizza",
            description = "now with cheese",
            latitude = null,
            longitude = null,
            publishedAtEpochMs = 100L,
            ratingSum = 8L,
            voterCount = 2L,
            ingredientsCsv = "cheese,tomato",
            classifierVersion = "v1",
            cuisine = "italian",
            kind = "solo",
            plateSource = "gallery",
            pending = 0L,
            idempotencyKey = null,
        )

        val rows = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList()
        assertEquals(1, rows.size)
        assertEquals("Pizza", rows.single().dishName)
        assertEquals(8L, rows.single().ratingSum)
        assertEquals("cheese,tomato", rows.single().ingredientsCsv)
        // The plateSource column round-trips (NULL default on the first insert, replaced here).
        assertEquals("gallery", rows.single().plateSource)
    }

    @Test fun select_range_by_crew_is_inclusive_on_both_ends() {
        insertMeal("a", crewId = "c1", dayKey = "2026-06-10", publishedAtEpochMs = 1L)
        insertMeal("b", crewId = "c1", dayKey = "2026-06-15", publishedAtEpochMs = 2L)
        insertMeal("c", crewId = "c1", dayKey = "2026-06-20", publishedAtEpochMs = 3L)
        insertMeal("d", crewId = "c1", dayKey = "2026-06-21", publishedAtEpochMs = 4L)

        val rows = db.mealQueries.selectRangeByCrew("c1", "2026-06-10", "2026-06-20").executeAsList()
        assertEquals(setOf("a", "b", "c"), rows.map { it.mealId }.toSet())
    }

    @Test fun delete_meals_by_ids_only_removes_listed_rows() {
        insertMeal("keep", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)
        insertMeal("gone1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 2L)
        insertMeal("gone2", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 3L)

        db.mealQueries.deleteMealsByIds(listOf("gone1", "gone2"))

        val remaining = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList()
        assertEquals(listOf("keep"), remaining.map { it.mealId })
    }

    @Test fun meal_ids_for_crew_in_range_returns_only_window() {
        insertMeal("in1", crewId = "c1", dayKey = "2026-06-12", publishedAtEpochMs = 1L)
        insertMeal("in2", crewId = "c1", dayKey = "2026-06-14", publishedAtEpochMs = 2L)
        insertMeal("old", crewId = "c1", dayKey = "2026-05-01", publishedAtEpochMs = 3L)

        val ids = db.mealQueries.mealIdsForCrewInRange("c1", "2026-06-10", "2026-06-20").executeAsList()
        assertEquals(setOf("in1", "in2"), ids.toSet())
    }

    @Test fun set_meal_pending_flips_the_flag() {
        insertMeal("m1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 1L, pending = 1L)
        assertEquals(1L, db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList().single().pending)

        db.mealQueries.setMealPending(pending = 0L, mealId = "m1")
        assertEquals(0L, db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList().single().pending)
    }

    @Test fun ratings_upsert_select_and_delete() {
        insertMeal("m1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)
        db.mealQueries.upsertRating("m1", "rater-1", score = 4L, atMs = 10L, pending = 0L, edited = 0L)
        db.mealQueries.upsertRating("m1", "rater-2", score = 5L, atMs = 11L, pending = 0L, edited = 0L)
        // Overwrite rater-1's score (PK collision → replace).
        db.mealQueries.upsertRating("m1", "rater-1", score = 3L, atMs = 12L, pending = 1L, edited = 1L)

        val ratings = db.mealQueries.selectRatingsForMeals(listOf("m1")).executeAsList()
        assertEquals(2, ratings.size)
        assertEquals(3L, ratings.first { it.raterId == "rater-1" }.score)
        assertEquals(1L, ratings.first { it.raterId == "rater-1" }.pending)
        // The `edited` flag round-trips through the column (set on rater-1's overwrite).
        assertEquals(1L, ratings.first { it.raterId == "rater-1" }.edited)
        assertEquals(0L, ratings.first { it.raterId == "rater-2" }.edited)

        db.mealQueries.deleteRating("m1", "rater-1")
        val afterDelete = db.mealQueries.selectRatingsForMeals(listOf("m1")).executeAsList()
        assertEquals(listOf("rater-2"), afterDelete.map { it.raterId })
    }

    @Test fun delete_meals_before_day_prunes_older_rows_and_cascades_ratings() {
        insertMeal("ancient", crewId = "c1", dayKey = "2026-01-01", publishedAtEpochMs = 1L)
        db.mealQueries.upsertRating("ancient", "rater-1", score = 5L, atMs = 1L, pending = 0L, edited = 0L)
        insertMeal("day-before", crewId = "c1", dayKey = "2026-03-20", publishedAtEpochMs = 2L)
        // Exactly the cutoff day → kept (delete is strictly `< cutoff`).
        insertMeal("on-cutoff", crewId = "c1", dayKey = "2026-03-21", publishedAtEpochMs = 3L)
        insertMeal("recent", crewId = "c1", dayKey = "2026-06-15", publishedAtEpochMs = 4L)
        db.mealQueries.upsertRating("recent", "rater-2", score = 4L, atMs = 5L, pending = 0L, edited = 0L)

        db.mealQueries.deleteMealsBeforeDay("2026-03-21")

        val remaining = db.mealQueries
            .selectRangeByCrew("c1", "2026-01-01", "2026-06-20").executeAsList().map { it.mealId }
        assertEquals(setOf("on-cutoff", "recent"), remaining.toSet())
        // The pruned meal's ratings cascaded away (foreign_keys ON in the test driver); the kept one survives.
        assertTrue(db.mealQueries.selectRatingsForMeals(listOf("ancient")).executeAsList().isEmpty())
        assertEquals(
            listOf("rater-2"),
            db.mealQueries.selectRatingsForMeals(listOf("recent")).executeAsList().map { it.raterId },
        )
    }

    @Test fun delete_ratings_for_absent_meals_keeps_ratings_with_a_live_meal() {
        // deleteRatingsForAbsentMeals is a defensive sweep for the production case where a connection
        // lacks foreign_keys (orphans then linger). Under the FK-enforcing test drivers an orphan
        // rating can't be inserted at all, so here we only assert the sweep is a no-op when every
        // rating still has a live meal — the cascade itself is covered by deleting_a_meal_cascades_*.
        insertMeal("present", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)
        db.mealQueries.upsertRating("present", "rater-1", score = 4L, atMs = 1L, pending = 0L, edited = 0L)

        db.mealQueries.deleteRatingsForAbsentMeals()

        assertEquals(
            listOf("rater-1"),
            db.mealQueries.selectRatingsForMeals(listOf("present")).executeAsList().map { it.raterId },
        )
    }

    @Test fun deleting_a_meal_cascades_to_its_ratings() {
        insertMeal("m1", crewId = "c1", dayKey = "2026-06-19", publishedAtEpochMs = 1L)
        db.mealQueries.upsertRating("m1", "rater-1", score = 4L, atMs = 10L, pending = 0L, edited = 0L)
        db.mealQueries.upsertRating("m1", "rater-2", score = 5L, atMs = 11L, pending = 0L, edited = 0L)
        assertEquals(2, db.mealQueries.selectRatingsForMeals(listOf("m1")).executeAsList().size)

        db.mealQueries.deleteMealsByIds(listOf("m1"))

        val orphanRatings = db.mealQueries.selectRatingsForMeals(listOf("m1")).executeAsList()
        assertTrue(orphanRatings.isEmpty(), "ON DELETE CASCADE should remove the meal's ratings")
    }
}
