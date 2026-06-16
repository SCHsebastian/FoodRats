package es.schsebastian.foodrats.app.consent

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {

    private val decisionFlow = MutableStateFlow<ConsentDecision>(ConsentDecision.Unknown)
    private val calls = mutableListOf<String>()
    private val consent = object : ConsentPort {
        override val decision = decisionFlow
        override suspend fun grant() { calls += "grant" }
        override suspend fun deny() { calls += "deny" }
        override suspend fun revoke() { calls += "revoke" }
    }
    private val analytics = RecordingAnalyticsTracker()

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = ConsentViewModel(consent = consent, analytics = analytics)

    @Test
    fun grant_writes_decision_then_records_consent_granted_event() = runTest {
        val vm = buildVm()
        vm.effects.test {
            vm.onIntent(ConsentIntent.Grant)
            assertEquals(ConsentEffect.Decided, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("grant"), calls)
        val expected: List<AnalyticsEvent> =
            listOf(AnalyticsEvent.ConsentGranted(AnalyticsConfig.CURRENT_CONSENT_VERSION))
        assertEquals(expected, analytics.events.toList())
    }

    @Test
    fun deny_writes_decision_and_records_no_event() = runTest {
        val vm = buildVm()
        vm.effects.test {
            vm.onIntent(ConsentIntent.Deny)
            assertEquals(ConsentEffect.Decided, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("deny"), calls)
        // Tracking is off when denied; emitting any event here would be a consent violation.
        assertTrue(analytics.events.isEmpty(), "deny must not record analytics")
    }

    @Test
    fun submitting_state_clears_after_a_decision() = runTest {
        val vm = buildVm()
        vm.onIntent(ConsentIntent.Grant)
        assertEquals(false, vm.state.value.isSubmitting)
    }
}
