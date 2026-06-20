package es.schsebastian.foodrats.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.preferences.DefaultAudienceError
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Round-trips [DefaultAudienceRepository] over an in-memory DataStore: absent reads emit `null`,
 * writes are observable, blank/empty stored values are treated as absent, and a failing store
 * maps to the typed [DefaultAudienceError.Persist.Unavailable].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudienceRepositoryTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private class ThrowingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = MutableStateFlow(mutablePreferencesOf())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IllegalStateException("disk unavailable")
    }

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val default = d
        override val io = d
    }

    private fun repo(store: DataStore<Preferences> = FakeDataStore()) = DefaultAudienceRepository(
        prefs = AppPreferences(store),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    )

    private val crewA = CrewId.of("crewA").getOrNull()!!
    private val crewB = CrewId.of("crewB").getOrNull()!!

    @Test
    fun default_audience_is_null_when_absent() = runTest {
        repo().defaultAudience.test {
            assertNull(awaitItem())
        }
    }

    @Test
    fun set_persists_and_emits_the_crew_set() = runTest {
        val r = repo()
        r.defaultAudience.test {
            assertNull(awaitItem())
            r.set(setOf(crewA, crewB))
            val saved = awaitItem()
            assertEquals(setOf(crewA, crewB), saved)
        }
    }

    @Test
    fun set_single_crew_round_trips() = runTest {
        val r = repo()
        r.set(setOf(crewA))
        r.defaultAudience.test {
            assertEquals(setOf(crewA), awaitItem())
        }
    }

    @Test
    fun set_empty_set_emits_null() = runTest {
        val r = repo()
        r.set(emptySet())
        r.defaultAudience.test {
            // An empty encoded string → blank → treated as absent → null
            assertNull(awaitItem())
        }
    }

    @Test
    fun set_returns_unit_on_success() = runTest {
        assertEquals(Result.success(Unit), repo().set(setOf(crewA)))
    }

    @Test
    fun set_maps_store_failure_to_persist_unavailable() = runTest {
        assertEquals(
            Result.failure(DefaultAudienceError.Persist.Unavailable),
            repo(ThrowingDataStore()).set(setOf(crewA)),
        )
    }
}
