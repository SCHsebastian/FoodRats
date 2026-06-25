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
        // meal table (same as in the generated v2 schema — it is unchanged across versions)
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
        // Ensure migrate(1→2) leaves pre-existing meal rows intact.
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
        FoodRatsDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)

        val db = FoodRatsDatabase(driver)
        val meals = db.mealQueries.selectFeedByCrewDay("c1", "2026-06-19").executeAsList()
        assertEquals(1, meals.size, "pre-existing meal row must survive the v1→v2 migration")
        assertEquals("m1", meals.single().mealId)

        driver.close()
    }
}
