package es.schsebastian.foodrats.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.isAnalyticsGranted
import es.schsebastian.foodrats.core.domain.analytics.needsDecision
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.time.FixedClock
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
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Round-trips [ConsentRepository] over an in-memory DataStore: every write stamps the current consent
 * version + decided-at, and absence reads back as [ConsentDecision.Unknown] (analytics hard no-op,
 * gate shows the consent screen). This is the persistence contract the routing/UI task depends on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentRepositoryTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())
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

    private val now = Instant.parse("2026-06-14T12:00:00Z")

    private fun repo() = ConsentRepository(
        prefs = AppPreferences(FakeDataStore()),
        clock = FixedClock(now),
        dispatchers = TestDispatchers(UnconfinedTestDispatcher()),
    )

    @Test
    fun absent_state_reads_as_unknown() = runTest {
        repo().decision.test {
            assertEquals(ConsentDecision.Unknown, awaitItem())
        }
    }

    @Test
    fun grant_records_current_version_and_decided_at() = runTest {
        val repo = repo()
        repo.decision.test {
            assertEquals(ConsentDecision.Unknown, awaitItem())
            repo.grant()
            val granted = assertIs<ConsentDecision.Granted>(awaitItem())
            assertEquals(AnalyticsConfig.CURRENT_CONSENT_VERSION, granted.version)
            assertEquals(now, granted.at)
            assertTrue(granted.isAnalyticsGranted)
            assertFalse(granted.needsDecision)
        }
    }

    @Test
    fun deny_records_denied_at_current_version() = runTest {
        val repo = repo()
        repo.decision.test {
            assertEquals(ConsentDecision.Unknown, awaitItem())
            repo.deny()
            val denied = assertIs<ConsentDecision.Denied>(awaitItem())
            assertEquals(AnalyticsConfig.CURRENT_CONSENT_VERSION, denied.version)
            assertFalse(denied.isAnalyticsGranted)
            assertFalse(denied.needsDecision, "an explicit current-version decline is settled")
        }
    }

    @Test
    fun revoke_after_grant_lands_on_denied() = runTest {
        val repo = repo()
        repo.grant()
        repo.decision.test {
            assertIs<ConsentDecision.Granted>(awaitItem())
            repo.revoke()
            val revoked = assertIs<ConsentDecision.Denied>(awaitItem())
            assertFalse(revoked.isAnalyticsGranted, "revoke stops tracking")
        }
    }
}
