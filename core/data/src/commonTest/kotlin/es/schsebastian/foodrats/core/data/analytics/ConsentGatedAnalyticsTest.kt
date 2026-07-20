package es.schsebastian.foodrats.core.data.analytics

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.RecordingAnalyticsTracker
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConsentGatedAnalyticsTest {

    private class FakeConsent(val state: MutableStateFlow<ConsentDecision>) : ConsentPort {
        override val decision: Flow<ConsentDecision> = state
        override suspend fun grant() { state.value = grantedNow() }
        override suspend fun deny() { state.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, EPOCH) }
        override suspend fun revoke() = deny()
    }

    private val accountId = AccountId.of("acc-1").getOrNull()!!

    @Test
    fun no_events_forwarded_while_consent_unknown() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(MutableStateFlow(ConsentDecision.Unknown)), backgroundScope)
        runCurrent()

        gate.track(AnalyticsEvent.MealComposerOpened)
        gate.setUserProperty(es.schsebastian.foodrats.core.domain.analytics.UserProperty.CREWS_COUNT, "2")

        assertTrue(recorder.events.isEmpty(), "events must not forward before consent")
        assertTrue(recorder.userProperties.isEmpty(), "user properties must not forward before consent")
    }

    @Test
    fun events_forward_once_consent_granted() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val consent = MutableStateFlow<ConsentDecision>(ConsentDecision.Unknown)
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(consent), backgroundScope)
        runCurrent()

        consent.value = grantedNow()
        runCurrent()
        gate.track(AnalyticsEvent.MealComposerOpened)

        assertEquals(listOf("meal_composer_opened"), recorder.eventNames())
        // applyConsent(true) was pushed to the SDK on the grant transition.
        assertTrue(recorder.consentApplications.last())
    }

    @Test
    fun setUserId_is_nulled_until_consent_then_real() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val consent = MutableStateFlow<ConsentDecision>(ConsentDecision.Unknown)
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(consent), backgroundScope)
        runCurrent()

        gate.setUserId(accountId)
        assertEquals(listOf<AccountId?>(null), recorder.userIds.toList(), "pre-consent identity must stay anonymous")

        consent.value = grantedNow()
        runCurrent()
        gate.setUserId(accountId)
        assertEquals(accountId, recorder.userIds.last())
    }

    @Test
    fun grant_stamps_data_consent_version_user_property() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val consent = MutableStateFlow<ConsentDecision>(ConsentDecision.Unknown)
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(consent), backgroundScope)
        runCurrent()
        assertTrue(recorder.userProperties.isEmpty(), "no property before a decision")

        consent.value = grantedNow()
        runCurrent()

        val stamped = recorder.userProperties
            .lastOrNull { it.first == es.schsebastian.foodrats.core.domain.analytics.UserProperty.DATA_CONSENT_VERSION }
        assertEquals(
            AnalyticsConfig.CURRENT_CONSENT_VERSION.toString(),
            stamped?.second,
            "granting must stamp data_consent_version with the agreed schema version",
        )
        gate.track(AnalyticsEvent.MealComposerOpened)
        assertEquals(listOf("meal_composer_opened"), recorder.eventNames())
    }

    @Test
    fun deny_does_not_stamp_data_consent_version() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val consent = MutableStateFlow<ConsentDecision>(ConsentDecision.Unknown)
        ConsentGatedAnalytics(recorder, FakeConsent(consent), backgroundScope)
        runCurrent()

        consent.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, EPOCH)
        runCurrent()

        assertTrue(recorder.userProperties.isEmpty(), "a denial must not stamp any user property")
    }

    @Test
    fun stale_version_grant_is_treated_as_not_granted() = runTest {
        val recorder = RecordingAnalyticsTracker()
        // A grant recorded at an OLDER consent version must force re-consent (no tracking).
        val stale = ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION - 1, EPOCH)
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(MutableStateFlow(stale)), backgroundScope)
        runCurrent()

        gate.track(AnalyticsEvent.MealComposerOpened)
        assertTrue(recorder.events.isEmpty(), "a below-current-version grant must not enable tracking")
    }

    @Test
    fun revoke_stops_forwarding_and_resets_sdk() = runTest {
        val recorder = RecordingAnalyticsTracker()
        val consent = MutableStateFlow<ConsentDecision>(grantedNow())
        val gate = ConsentGatedAnalytics(recorder, FakeConsent(consent), backgroundScope)
        runCurrent()
        gate.track(AnalyticsEvent.MealComposerOpened)
        assertEquals(1, recorder.events.size)

        consent.value = ConsentDecision.Denied(AnalyticsConfig.CURRENT_CONSENT_VERSION, EPOCH)
        runCurrent()
        gate.track(AnalyticsEvent.MealComposerOpened)

        assertEquals(1, recorder.events.size, "no events after revoke")
        assertTrue(recorder.resetCount >= 1, "SDK data reset on revoke")
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
        fun grantedNow() = ConsentDecision.Granted(AnalyticsConfig.CURRENT_CONSENT_VERSION, EPOCH)
    }
}
