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
}
