package es.schsebastian.foodrats.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.preferences.CURRENT_EULA_VERSION
import es.schsebastian.foodrats.core.domain.preferences.needsEulaAcceptance
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips [EulaRepository] over an in-memory DataStore (mirrors `ConsentRepositoryTest`): absence
 * reads back as "never accepted" (gate requires acceptance), `accept(version)` persists the version
 * (gate settles), and a below-current stored version still needs re-acceptance. This is the persistence
 * contract the login-screen acceptance gate depends on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EulaRepositoryTest {

    private class FakeDataStore(initial: Preferences = mutablePreferencesOf()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val newValue = transform(state.value)
            state.value = newValue
            return newValue
        }
    }

    private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val main = d
        override val default = d
        override val io = d
    }

    private fun repo(store: DataStore<Preferences> = FakeDataStore()) = EulaRepository(
        prefs = AppPreferences(store),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    )

    @Test
    fun absent_version_reads_as_never_accepted() = runTest {
        repo().acceptedVersion.test {
            val v = awaitItem()
            assertNull(v)
            assertTrue(needsEulaAcceptance(CURRENT_EULA_VERSION, v))
        }
    }

    @Test
    fun accept_persists_version_and_settles_the_gate() = runTest {
        val repo = repo()
        repo.acceptedVersion.test {
            assertNull(awaitItem())
            val outcome = repo.accept(CURRENT_EULA_VERSION)
            assertIs<Result.Ok<Unit>>(outcome)
            val v = awaitItem()
            assertEquals(CURRENT_EULA_VERSION, v)
            assertFalse(needsEulaAcceptance(CURRENT_EULA_VERSION, v))
        }
    }

    @Test
    fun below_current_stored_version_still_needs_reacceptance() = runTest {
        val seeded = mutablePreferencesOf().apply {
            this[intPreferencesKey("eula_accepted_version")] = CURRENT_EULA_VERSION
        }
        repo(FakeDataStore(seeded)).acceptedVersion.test {
            val v = awaitItem()
            assertEquals(CURRENT_EULA_VERSION, v)
            // A future bump (current + 1) must re-prompt a user who only accepted the current version.
            assertTrue(needsEulaAcceptance(CURRENT_EULA_VERSION + 1, v))
        }
    }
}
