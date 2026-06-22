package es.schsebastian.foodrats.core.database

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-platform behavior of the `outbox` table (offline-first P3b-T6). Runs on the host JVM
 * (`testAndroidHostTest`) and the iOS simulator (`iosSimulatorArm64Test`) via [createInMemorySqlDriver].
 * The store's domain mapping is unit-tested in `:core:data`'s `OutboxLocalStoreTest`; this locks the
 * SQL contract the store depends on: createdAt ordering, idempotency-key coalescing (UNIQUE index +
 * INSERT OR REPLACE), the statusKind filter, and updateStatus / deleteById.
 */
class OutboxTableTest {
    private lateinit var db: FoodRatsDatabase

    @BeforeTest fun setUp() {
        db = FoodRatsDatabase(createInMemorySqlDriver())
    }

    @AfterTest fun tearDown() = Unit

    @Suppress("LongParameterList")
    private fun insert(
        id: String,
        idempotencyKey: String,
        statusKind: String = "pending",
        createdAtEpochMs: Long,
        attemptCount: Long = 0L,
        retryable: Long = 0L,
        errorKey: String? = null,
    ) = db.outboxQueries.upsertByIdem(
        id = id,
        type = "rate_meal",
        idempotencyKey = idempotencyKey,
        statusKind = statusKind,
        errorKey = errorKey,
        retryable = retryable,
        attemptCount = attemptCount,
        createdAtEpochMs = createdAtEpochMs,
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
        focalY = null,
        setAtMillis = null,
        styleKey = null,
    )

    @Test fun select_all_orders_by_created_at_ascending() {
        insert("e2", idempotencyKey = "k2", createdAtEpochMs = 200L)
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L)
        insert("e3", idempotencyKey = "k3", createdAtEpochMs = 300L)

        val rows = db.outboxQueries.selectAll().executeAsList()
        assertEquals(listOf("e1", "e2", "e3"), rows.map { it.id })
    }

    @Test fun upsert_coalesces_on_idempotency_key_last_write_wins() {
        insert("e1", idempotencyKey = "same", createdAtEpochMs = 100L, attemptCount = 0L)
        // A second row with the SAME idempotency key but a different id replaces the first.
        insert("e2", idempotencyKey = "same", createdAtEpochMs = 200L, attemptCount = 3L)

        val rows = db.outboxQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals("e2", rows.single().id) // REPLACE dropped the prior row sharing the key
        assertEquals(3L, rows.single().attemptCount)
    }

    @Test fun upsert_replaces_on_same_primary_key() {
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L)
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L, statusKind = "uploading")

        val rows = db.outboxQueries.selectAll().executeAsList()
        assertEquals(1, rows.size)
        assertEquals("uploading", rows.single().statusKind)
    }

    @Test fun select_pending_returns_only_pending_rows() {
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L, statusKind = "pending")
        insert("e2", idempotencyKey = "k2", createdAtEpochMs = 200L, statusKind = "failed", retryable = 1L, errorKey = "x")
        insert("e3", idempotencyKey = "k3", createdAtEpochMs = 300L, statusKind = "pending")

        val pending = db.outboxQueries.selectPending().executeAsList()
        assertEquals(listOf("e1", "e3"), pending.map { it.id })
    }

    @Test fun update_status_mutates_lifecycle_columns() {
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L)

        db.outboxQueries.updateStatus(
            statusKind = "failed",
            errorKey = "rate.offline",
            retryable = 1L,
            attemptCount = 2L,
            lastAttemptAtEpochMs = 999L,
            id = "e1",
        )

        val row = db.outboxQueries.selectAll().executeAsList().single()
        assertEquals("failed", row.statusKind)
        assertEquals("rate.offline", row.errorKey)
        assertEquals(1L, row.retryable)
        assertEquals(2L, row.attemptCount)
        assertEquals(999L, row.lastAttemptAtEpochMs)
    }

    @Test fun delete_by_id_removes_the_row_and_is_noop_when_absent() {
        insert("e1", idempotencyKey = "k1", createdAtEpochMs = 100L)
        db.outboxQueries.deleteById("e1")
        assertTrue(db.outboxQueries.selectAll().executeAsList().isEmpty())
        db.outboxQueries.deleteById("e1") // no-op, no throw
    }
}
