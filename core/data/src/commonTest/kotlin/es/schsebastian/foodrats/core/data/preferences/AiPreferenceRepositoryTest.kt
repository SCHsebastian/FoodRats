package es.schsebastian.foodrats.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.AiPreferenceError
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Round-trips [AiPreferenceRepository] over an in-memory DataStore: absent reads default to
 * opt-in (`enabled = true`), writes are observable, and a failing store maps to the typed
 * [AiPreferenceError.Persist.Unavailable] (never a thrown exception). This is the
 * persistence contract the AI gate in [ClassifyDraftPlateUseCase] depends on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiPreferenceRepositoryTest {

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

    private fun repo(store: DataStore<Preferences> = FakeDataStore()) = AiPreferenceRepository(
        prefs = AppPreferences(store),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    )

    @Test
    fun enabled_defaults_to_true_when_absent() = runTest {
        repo().enabled.test {
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun set_false_then_true_emits_each_value() = runTest {
        val repo = repo()
        repo.enabled.test {
            assertEquals(true, awaitItem())
            repo.set(false)
            assertEquals(false, awaitItem())
            repo.set(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun set_returns_unit_on_success() = runTest {
        assertEquals(Result.success(Unit), repo().set(false))
    }

    @Test
    fun set_maps_store_failure_to_persist_unavailable() = runTest {
        assertEquals(
            Result.failure(AiPreferenceError.Persist.Unavailable),
            repo(ThrowingDataStore()).set(true),
        )
    }
}
