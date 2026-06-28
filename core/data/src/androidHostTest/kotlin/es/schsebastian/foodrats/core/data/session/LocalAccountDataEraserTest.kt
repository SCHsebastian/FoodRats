package es.schsebastian.foodrats.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Security #3 — [LocalAccountDataEraser] must wipe the account-scoped SQLDelight cache and the
 * account-scoped DataStore keys on sign-out, while preserving device/human-scoped preferences.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalAccountDataEraserTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: FoodRatsDatabase
    private lateinit var prefs: AppPreferences
    private lateinit var eraser: LocalAccountDataEraser

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply { FoodRatsDatabase.Schema.create(this) }
        database = FoodRatsDatabase(driver)
        prefs = AppPreferences(FakeDataStore())
        eraser = LocalAccountDataEraser(database, prefs, dispatchers)
    }

    @AfterTest fun tearDown() = driver.close()

    @Test
    fun erases_cached_crew_and_ratings_and_account_prefs_but_keeps_device_prefs() = runTest {
        // Seed the cache with the previous user's data.
        // blindVoting is stored as INTEGER (Long) by SQLDelight; 0 = false.
        database.crewQueries.upsert("c1", "Saturday Brunch", "alice", 0L, "alice,bob", 1L)
        database.mealQueries.upsertRating("m1", "alice", 5L, 1L, 0L, 0L)
        // Account-scoped prefs + a device-scoped pref that must SURVIVE sign-out.
        prefs.set(Keys.ActiveCrewId, "c1")
        prefs.set(Keys.PlateUrlCacheJson, """{"crews/c1/meals/m.jpg":{"url":"u","freshUntilMs":9}}""")
        prefs.set(Keys.ThemeMode, "dark")

        assertTrue(database.crewQueries.selectAll().executeAsList().isNotEmpty())

        eraser.eraseLocalAccountData()

        assertTrue(database.crewQueries.selectAll().executeAsList().isEmpty(), "cached crews wiped")
        assertEquals(null, prefs.observe(Keys.ActiveCrewId).first(), "active crew (account-scoped) cleared")
        assertEquals(null, prefs.observe(Keys.PlateUrlCacheJson).first(), "signed-URL cache (account-scoped) cleared")
        assertEquals("dark", prefs.observe(Keys.ThemeMode).first(), "theme (device-scoped) preserved")
    }
}
