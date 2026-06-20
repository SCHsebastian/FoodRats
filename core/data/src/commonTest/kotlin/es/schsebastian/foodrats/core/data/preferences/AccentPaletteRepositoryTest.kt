package es.schsebastian.foodrats.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AccentPaletteError
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
 * Round-trips [AccentPaletteRepository] over an in-memory DataStore: absent reads default to
 * [AccentPalette.Ember], writes are observable, and a failing store maps to the typed
 * [AccentPaletteError.Persist.Unavailable] (never a thrown exception).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccentPaletteRepositoryTest {

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

    private fun repo(store: DataStore<Preferences> = FakeDataStore()) = AccentPaletteRepository(
        prefs = AppPreferences(store),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    )

    @Test
    fun palette_defaults_to_ember_when_absent() = runTest {
        repo().palette.test {
            assertEquals(AccentPalette.Ember, awaitItem())
        }
    }

    @Test
    fun set_moss_then_steel_emits_each_value() = runTest {
        val repo = repo()
        repo.palette.test {
            assertEquals(AccentPalette.Ember, awaitItem())
            repo.set(AccentPalette.Moss)
            assertEquals(AccentPalette.Moss, awaitItem())
            repo.set(AccentPalette.Steel)
            assertEquals(AccentPalette.Steel, awaitItem())
        }
    }

    @Test
    fun set_returns_unit_on_success() = runTest {
        assertEquals(Result.success(Unit), repo().set(AccentPalette.Rust))
    }

    @Test
    fun set_maps_store_failure_to_persist_unavailable() = runTest {
        assertEquals(
            Result.failure(AccentPaletteError.Persist.Unavailable),
            repo(ThrowingDataStore()).set(AccentPalette.Berry),
        )
    }

    @Test
    fun all_palette_variants_round_trip() = runTest {
        val repo = repo()
        for (variant in AccentPalette.entries) {
            repo.set(variant)
            repo.palette.test {
                assertEquals(variant, awaitItem())
            }
        }
    }
}
