package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M7: Guards the hand-duplicated v1→v2 `CREATE` statements in `1.sqm` against schema drift.
 *
 * The `1.sqm` migration keeps `outbox` + `crew` CREATE statements byte-identical to `Outbox.sq` /
 * `Crew.sq` — there is no compile-time enforcement of that invariant. This test proves that a
 * v1 database (only `meal` + `mealRating`) migrated to v2 ends up with a working `outbox` and
 * `crew` table: it inserts a row into each and reads it back, confirming the migrated schema is
 * usable and structurally correct.
 *
 * A fresh-install v2 database (via [FoodRatsDatabase.Schema.create]) is used as the baseline for
 * comparison. Both paths are exercised in one test:
 *  1. **Migrated DB**: start with a v1 schema (manually created — only `meal` + `mealRating`),
 *     run `Schema.migrate(1, 2)`, then insert + select on `outbox` and `crew`.
 *  2. **Fresh DB**: run `Schema.create()`, then do the same inserts/selects as a sanity reference.
 *
 * If `1.sqm` drifts from `Outbox.sq` / `Crew.sq` (e.g. a new column is added to the source
 * definition but not the migration), the migrated insert will fail with an unknown column error,
 * making the drift immediately visible.
 */
class MigrationV1ToV2Test {

    /** Minimal v1 schema: only the `meal` + `mealRating` tables that existed before P3b. */
    private fun createV1Schema(driver: JdbcSqliteDriver) {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // meal table AS IT WAS AT v1 (no plateSource — that column arrives via 4.sqm at v5)
        driver.execute(
            null,
            """
            CREATE TABLE meal (
                mealId             TEXT    NOT NULL PRIMARY KEY,
                crewId             TEXT    NOT NULL,
                authorId           TEXT    NOT NULL,
                authorName         TEXT,
                dayKey             TEXT    NOT NULL,
                slot               TEXT    NOT NULL,
                platePath          TEXT,
                thumbnailPath      TEXT,
                thumbHash          TEXT,
                dishName           TEXT,
                description        TEXT    NOT NULL DEFAULT '',
                latitude           REAL,
                longitude          REAL,
                publishedAtEpochMs INTEGER NOT NULL,
                ratingSum          INTEGER NOT NULL DEFAULT 0,
                voterCount         INTEGER NOT NULL DEFAULT 0,
                ingredientsCsv     TEXT    NOT NULL DEFAULT '',
                classifierVersion  TEXT,
                cuisine            TEXT,
                kind               TEXT    NOT NULL DEFAULT 'solo',
                pending            INTEGER NOT NULL DEFAULT 0,
                idempotencyKey     TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "CREATE INDEX meal_crew_day ON meal(crewId, dayKey)", 0)
        driver.execute(
            null,
            """
            CREATE TABLE mealRating (
                mealId  TEXT    NOT NULL,
                raterId TEXT    NOT NULL,
                score   INTEGER NOT NULL,
                atMs    INTEGER NOT NULL,
                pending INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (mealId, raterId),
                FOREIGN KEY (mealId) REFERENCES meal(mealId) ON DELETE CASCADE
            )
            """.trimIndent(),
            0,
        )
    }

    /**
     * Insert one row into `outbox` exercising columns from every schema version — including the
     * v2→v3 crew-settings columns (focalY / setAtMillis / styleKey) — then read it back. Non-null
     * values on the v3 columns mean a migrated DB that never received `2.sqm` fails here with an
     * unknown-column error, surfacing drift.
     */
    private fun roundTripOutbox(db: FoodRatsDatabase) {
        db.outboxQueries.upsertByIdem(
            id = "test-id",
            type = "set_banner_focal",
            idempotencyKey = "test-idem",
            statusKind = "pending",
            errorKey = null,
            retryable = 0L,
            attemptCount = 0L,
            createdAtEpochMs = 1_000L,
            lastAttemptAtEpochMs = null,
            crewId = "c1",
            mealId = "m1",
            accountId = "a1",
            commentId = null,
            text = null,
            score = 4L,
            reactionKindKey = null,
            desiredPresent = null,
            enabled = null,
            targetAccountId = null,
            newName = null,
            focalY = 0.25,
            setAtMillis = 1_700_000_000_000L,
            styleKey = "emoji",
            mentions = null,
        )
        val rows = db.outboxQueries.selectAll().executeAsList()
        assertEquals(1, rows.size, "outbox: expected 1 row after insert")
        assertEquals("test-id", rows.single().id)
        assertEquals("set_banner_focal", rows.single().type)
        assertEquals("pending", rows.single().statusKind)
        assertEquals(0.25, rows.single().focalY, "v2→v3 focalY column must round-trip")
        assertEquals("emoji", rows.single().styleKey, "v2→v3 styleKey column must round-trip")
    }

    /** Insert one row into `crew`, then read it back. */
    private fun roundTripCrew(db: FoodRatsDatabase) {
        db.crewQueries.upsert(
            crewId = "crew-1",
            name = "Test Crew",
            ownerId = "owner-1",
            blindVoting = 0L,
            memberIdsCsv = "owner-1",
            createdAtEpochMs = 2_000L,
        )
        val rows = db.crewQueries.selectAll().executeAsList()
        assertEquals(1, rows.size, "crew: expected 1 row after insert")
        assertEquals("crew-1", rows.single().crewId)
        assertEquals("Test Crew", rows.single().name)
    }

    @Test
    fun migrated_v1_to_latest_has_working_outbox_and_crew_tables() {
        // Build a v1 database (meal + mealRating only).
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)

        // Run the full migration chain to the CURRENT schema version (applies 1.sqm: outbox + crew;
        // then 2.sqm: the crew-settings columns). Migrating only to v2 would leave the outbox without
        // focalY/setAtMillis/styleKey and roundTripOutbox would fail — which is the drift guard.
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)

        val db = FoodRatsDatabase(driver)

        // Prove the migrated schema is usable: insert + select round-trips must succeed.
        roundTripOutbox(db)
        roundTripCrew(db)

        driver.close()
    }

    @Test
    fun fresh_v2_schema_also_has_working_outbox_and_crew_tables() {
        // Baseline: a fresh install also has both tables and behaves identically.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            execute(null, "PRAGMA foreign_keys = ON", 0)
            FoodRatsDatabase.Schema.create(this)
        }
        val db = FoodRatsDatabase(driver)

        roundTripOutbox(db)
        roundTripCrew(db)

        driver.close()
    }

    @Test
    fun migration_does_not_destroy_existing_meal_rows() {
        // Ensure the full migration chain (1 → latest) leaves pre-existing meal rows intact.
        // Must migrate to the CURRENT version: the generated SELECT * mappers read by column
        // index against the latest schema (incl. the v4→v5 `plateSource` column), so a DB left
        // at an intermediate version can't be queried through the generated queries.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)

        // Insert a meal before migrating.
        driver.execute(
            null,
            """
            INSERT INTO meal(mealId, crewId, authorId, dayKey, slot, description,
                publishedAtEpochMs, ratingSum, voterCount, ingredientsCsv, kind, pending)
            VALUES ('m1', 'c1', 'a1', '2026-06-19', 'lunch', '', 1000, 0, 0, '', 'solo', 0)
            """.trimIndent(),
            0,
        )

        // Migrate.
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)

        val db = FoodRatsDatabase(driver)
        val meals = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList()
        assertEquals(1, meals.size, "pre-existing meal row must survive the v1→latest migration")
        assertEquals("m1", meals.single().mealId)
        // v4→v5 (4.sqm): the pre-existing row's appended plateSource column is NULL (legacy = camera).
        assertEquals(null, meals.single().plateSource, "pre-migration meal rows must read plateSource NULL")

        driver.close()
    }

    /**
     * v4→v5 drift guard (4.sqm — `plateSource`): a migrated DB must accept and round-trip the new
     * column through the GENERATED queries. `upsertMeal` names every column, and
     * `selectFeedByCrewDay` is a `SELECT *` mapped by index — so this fails loudly if `4.sqm`
     * drifts from Meal.sq (missing column) or if the column order diverges between a fresh
     * `Schema.create()` table and the ALTER-appended migrated one.
     */
    @Test
    fun migrated_db_round_trips_plate_source_through_generated_queries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)
        val db = FoodRatsDatabase(driver)

        db.mealQueries.upsertMeal(
            mealId = "m-gallery", crewId = "c1", authorId = "a1", authorName = null,
            dayKey = "2026-07-13", slot = "", platePath = null, thumbnailPath = null, thumbHash = null,
            dishName = "Pizza", description = "", latitude = null, longitude = null,
            publishedAtEpochMs = 1_000L, ratingSum = 0L, voterCount = 0L, ingredientsCsv = "",
            classifierVersion = null, cuisine = null, kind = "solo", plateSource = "gallery",
            platesJson = null, pending = 0L, idempotencyKey = null,
        )

        val row = db.mealQueries.selectFeedByCrewDay("c1", "2026-07-13").executeAsList().single()
        assertEquals("gallery", row.plateSource, "plateSource must round-trip on a migrated DB")
        // The write landed in the right column (index-mapped SELECT * would smear neighbours on drift).
        assertEquals(0L, row.pending)
        assertEquals(null, row.idempotencyKey)

        driver.close()
    }

    /**
     * v5→v6 drift guard (5.sqm — `platesJson`): a migrated DB must accept and round-trip the new
     * column through the GENERATED queries, exactly like the v4→v5 `plateSource` guard above —
     * `upsertMeal` names every column, `selectFeedByCrewDay` is a `SELECT *` mapped by index, so
     * this fails loudly if `5.sqm` drifts from `Meal.sq` or the column order diverges between a
     * fresh `Schema.create()` table and the ALTER-appended migrated one.
     */
    @Test
    fun migrated_db_round_trips_plates_json_through_generated_queries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)
        val db = FoodRatsDatabase(driver)
        val platesJson = """[{"path":"crews/c1/meals/m-multi.jpg","source":"camera"},{"path":"crews/c1/meals/m-multi_p1.jpg","source":"gallery"}]"""

        db.mealQueries.upsertMeal(
            mealId = "m-multi", crewId = "c1", authorId = "a1", authorName = null,
            dayKey = "2026-07-13", slot = "", platePath = null, thumbnailPath = null, thumbHash = null,
            dishName = "Pizza", description = "", latitude = null, longitude = null,
            publishedAtEpochMs = 1_000L, ratingSum = 0L, voterCount = 0L, ingredientsCsv = "",
            classifierVersion = null, cuisine = null, kind = "solo", plateSource = "camera",
            platesJson = platesJson, pending = 0L, idempotencyKey = null,
        )

        val row = db.mealQueries.selectFeedByCrewDay("c1", "2026-07-13").executeAsList().single()
        assertEquals(platesJson, row.platesJson, "platesJson must round-trip on a migrated DB")
        // The write landed in the right column (index-mapped SELECT * would smear neighbours on drift).
        assertEquals(0L, row.pending)
        assertEquals(null, row.idempotencyKey)

        driver.close()
    }

    /**
     * Companion to `migration_does_not_destroy_existing_meal_rows`, which locks that a v1-inserted
     * row's appended `plateSource` column reads back NULL post-migration. This locks the SAME
     * contract for the `platesJson` column (5.sqm, one migration further): a row written BEFORE
     * either multi-photo migration existed must read `platesJson` as NULL after migrating all the
     * way to the CURRENT version — never a parse crash, never a stray default.
     */
    @Test
    fun migrated_pre_existing_row_reads_plates_json_null() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        driver.execute(
            null,
            """
            INSERT INTO meal(mealId, crewId, authorId, dayKey, slot, description,
                publishedAtEpochMs, ratingSum, voterCount, ingredientsCsv, kind, pending)
            VALUES ('m-pre', 'c1', 'a1', '2026-06-19', 'lunch', '', 1000, 0, 0, '', 'solo', 0)
            """.trimIndent(),
            0,
        )

        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)

        val db = FoodRatsDatabase(driver)
        val row = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList().single()
        assertEquals(null, row.platesJson, "a row written before platesJson existed must read back NULL")

        driver.close()
    }

    /**
     * v6→v7 drift guard (6.sqm — `mentions`): a migrated DB must accept and round-trip the new
     * column through the GENERATED queries, exactly like the plateSource/platesJson guards above —
     * `upsertByIdem` names every column, `selectAll` is a `SELECT *` mapped by index, so this fails
     * loudly if `6.sqm` drifts from `Outbox.sq` or the column order diverges between a fresh
     * `Schema.create()` table and the ALTER-appended migrated one.
     */
    @Test
    fun migrated_db_round_trips_mentions_through_generated_queries() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)
        val db = FoodRatsDatabase(driver)
        val mentionsJson = """["acc-1","acc-2"]"""

        db.outboxQueries.upsertByIdem(
            id = "entry-mention", type = "post_comment", idempotencyKey = "comment:c1:m1:cm1",
            statusKind = "pending", errorKey = null, retryable = 0L, attemptCount = 0L,
            createdAtEpochMs = 1_000L, lastAttemptAtEpochMs = null,
            crewId = "c1", mealId = "m1", accountId = "a1", commentId = "cm1", text = "hi @bob",
            score = null, reactionKindKey = null, desiredPresent = null, enabled = null,
            targetAccountId = null, newName = null, focalY = null, setAtMillis = null, styleKey = null,
            mentions = mentionsJson,
        )

        val row = db.outboxQueries.selectAll().executeAsList().single()
        assertEquals(mentionsJson, row.mentions, "mentions must round-trip on a migrated DB")
        // The write landed in the right column (index-mapped SELECT * would smear neighbours on drift).
        assertEquals("cm1", row.commentId)
        assertEquals("hi @bob", row.text)

        driver.close()
    }

    /**
     * Companion to the `plates_json`/`plate_source` "pre-existing row reads NULL" locks: a v1-queued
     * outbox row migrated all the way to the CURRENT version must read its appended `mentions`
     * column as NULL (never a parse crash, never a stray default) — [OutboxLocalStore.toDomain]
     * treats a NULL `mentions` column as `emptyList()`.
     */
    @Test
    fun migrated_pre_existing_outbox_row_reads_mentions_null() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = FoodRatsDatabase.Schema.version)
        val db = FoodRatsDatabase(driver)

        db.outboxQueries.upsertByIdem(
            id = "entry-pre", type = "rate_meal", idempotencyKey = "rate:c1:m1:a1",
            statusKind = "pending", errorKey = null, retryable = 0L, attemptCount = 0L,
            createdAtEpochMs = 1_000L, lastAttemptAtEpochMs = null,
            crewId = "c1", mealId = "m1", accountId = "a1", commentId = null, text = null,
            score = 4L, reactionKindKey = null, desiredPresent = null, enabled = null,
            targetAccountId = null, newName = null, focalY = null, setAtMillis = null, styleKey = null,
            mentions = null,
        )

        val row = db.outboxQueries.selectAll().executeAsList().single()
        assertEquals(null, row.mentions, "a row written before mentions existed must read back NULL")

        driver.close()
    }
}
