package es.schsebastian.foodrats.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppPreferencesTest {
    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private val testKey = StoreKey(stringPreferencesKey("test_key"))

    @Test fun observe_emits_null_when_unset_then_value_after_set() = runTest {
        val prefs = AppPreferences(FakeDataStore())
        prefs.observe(testKey).test {
            assertNull(awaitItem())
            prefs.set(testKey, "hello")
            assertEquals("hello", awaitItem())
        }
    }

    @Test fun clear_removes_value() = runTest {
        val prefs = AppPreferences(FakeDataStore())
        prefs.set(testKey, "x")
        prefs.observe(testKey).test {
            assertEquals("x", awaitItem())
            prefs.clear(testKey)
            assertNull(awaitItem())
        }
    }
}
