package es.schsebastian.foodrats.core.data.outbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one-shot P2-DataStore-JSON → SQLDelight-table migration (P3b-T6). Seeds a legacy [Keys.OutboxJson]
 * blob, runs [OutboxJsonMigration], and asserts the rows land in the table (coalesced on idempotency
 * key) and the legacy key is cleared so the migration is idempotent across launches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxJsonMigrationTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private lateinit var db: OutboxTestDb
    private lateinit var backing: FakeDataStore
    private lateinit var prefs: AppPreferences
    private lateinit var store: OutboxLocalStore

    @BeforeTest fun setUp() {
        db = OutboxTestDb()
        backing = FakeDataStore()
        prefs = AppPreferences(backing)
        store = db.store()
    }

    @AfterTest fun tearDown() = db.close()

    private fun migration() = OutboxJsonMigration(prefs, store, db.dispatchers)

    @Test
    fun migrates_legacy_entries_into_the_table_and_clears_the_key() = runTest {
        // Two distinct rate commands (different raters → distinct idempotency keys) in the legacy shape.
        val legacy = """
            [
              {"id":"e1","command":{"type":"rate_meal","crewId":"c1","mealId":"m1","accountId":"a1","score":4},
               "status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":1000},
              {"id":"e2","command":{"type":"post_comment","crewId":"c1","mealId":"m1","commentId":"cm1","text":"hi","accountId":"a1"},
               "status":{"kind":"failed","errorKey":"comment.offline","retryable":true},"attemptCount":2,"createdAtEpochMs":2000}
            ]
        """.trimIndent()
        prefs.set(Keys.OutboxJson, legacy)

        migration().run()

        val rows = store.read().sortedBy { it.id.value }
        assertEquals(listOf("e1", "e2"), rows.map { it.id.value })
        assertEquals(2, rows.first { it.id.value == "e2" }.attemptCount)
        // Legacy key cleared → idempotent across launches.
        assertNull(prefs.observe(Keys.OutboxJson).first())
    }

    @Test
    fun is_noop_when_no_legacy_blob_present() = runTest {
        migration().run()
        assertTrue(store.read().isEmpty())
        assertNull(prefs.observe(Keys.OutboxJson).first())
    }

    @Test
    fun second_run_does_not_re_import() = runTest {
        prefs.set(
            Keys.OutboxJson,
            """[{"id":"e1","command":{"type":"leave_crew","crewId":"c1","accountId":"a1"},"status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":1000}]""",
        )
        migration().run()
        assertEquals(1, store.read().size)
        // A second run (e.g. a later launch) finds no key and is a no-op.
        migration().run()
        assertEquals(1, store.read().size)
    }

    // ─── malformed-blob tolerance ──────────────────────────────────────────────

    /**
     * A totally corrupt/truncated top-level blob (not even valid JSON) fails to decode as a whole
     * — `runCatching { json.decodeFromString<...> }` catches the `SerializationException`,
     * `getOrNull()` yields `null`, and `.orEmpty()` treats it as zero entries. `run()` must not
     * crash and must still clear the legacy key (documents current behavior: an unparsable blob
     * has no recoverable entries, and clearing it prevents retrying the same corrupt blob forever).
     */
    @Test
    fun corrupt_top_level_blob_is_skipped_without_crashing_and_clears_the_key() = runTest {
        prefs.set(Keys.OutboxJson, "{not-even-json[[[")

        migration().run()

        assertTrue(store.read().isEmpty(), "a fully corrupt blob yields no migrated entries")
        assertNull(prefs.observe(Keys.OutboxJson).first(), "legacy key must still be cleared, not retried forever")
    }

    /** A truncated (cut-off mid-object) blob is the same "fails to decode at all" case as above. */
    @Test
    fun truncated_top_level_blob_is_skipped_without_crashing_and_clears_the_key() = runTest {
        prefs.set(Keys.OutboxJson, """[{"id":"e1","command":{"type":"rate_meal""")

        migration().run()

        assertTrue(store.read().isEmpty(), "a truncated blob yields no migrated entries")
        assertNull(prefs.observe(Keys.OutboxJson).first())
    }

    /**
     * The top-level JSON list itself decodes fine, but individual entries are malformed in ways
     * [OutboxJsonMigration]'s per-entry mapping is null-tolerant to: an unknown command
     * discriminator, a `rate_meal` missing its required `score`, and a blank `id`. Each malformed
     * entry is dropped via `mapNotNull`, and does NOT block the sibling valid entry from migrating.
     */
    @Test
    fun partially_valid_list_imports_only_the_valid_entries() = runTest {
        val legacy = """
            [
              {"id":"valid-1","command":{"type":"rate_meal","crewId":"c1","mealId":"m1","accountId":"a1","score":4},
               "status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":1000},
              {"id":"unknown-type","command":{"type":"unknown_op","crewId":"c1"},
               "status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":2000},
              {"id":"missing-score","command":{"type":"rate_meal","crewId":"c1","mealId":"m1","accountId":"a1"},
               "status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":3000},
              {"id":"","command":{"type":"rate_meal","crewId":"c1","mealId":"m1","accountId":"a1","score":5},
               "status":{"kind":"pending"},"attemptCount":0,"createdAtEpochMs":4000}
            ]
        """.trimIndent()
        prefs.set(Keys.OutboxJson, legacy)

        migration().run()

        val rows = store.read()
        assertEquals(1, rows.size, "only the single fully-valid entry must be imported")
        assertEquals("valid-1", rows.single().id.value)
        assertNull(prefs.observe(Keys.OutboxJson).first(), "legacy key cleared even with partial import")
    }
}
