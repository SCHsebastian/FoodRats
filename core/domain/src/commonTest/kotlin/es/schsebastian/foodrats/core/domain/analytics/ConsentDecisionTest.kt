package es.schsebastian.foodrats.core.domain.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Locks the consent decision/version model the routing gate (`w0-consent-ui-presentation`) and the
 * analytics gate (`ConsentGatedAnalytics`) rely on: `isAnalyticsGranted` (tracking switch) and
 * `needsDecision` (show-the-consent-screen switch) must stay consistent as `CURRENT_CONSENT_VERSION`
 * moves.
 */
class ConsentDecisionTest {

    private val current = AnalyticsConfig.CURRENT_CONSENT_VERSION

    @Test
    fun unknown_is_not_granted_and_needs_decision() {
        val d = ConsentDecision.Unknown
        assertFalse(d.isAnalyticsGranted)
        assertTrue(d.needsDecision, "no decision yet => show consent screen")
    }

    @Test
    fun current_version_grant_is_granted_and_settled() {
        val d = ConsentDecision.Granted(version = current, at = EPOCH)
        assertTrue(d.isAnalyticsGranted)
        assertFalse(d.needsDecision, "a current grant is settled => no re-prompt")
    }

    @Test
    fun current_version_deny_is_not_granted_but_settled() {
        val d = ConsentDecision.Denied(version = current, at = EPOCH)
        assertFalse(d.isAnalyticsGranted, "deny never enables tracking")
        assertFalse(d.needsDecision, "an explicit current-version decline is settled => no re-prompt")
    }

    @Test
    fun stale_grant_is_not_granted_and_needs_reconsent() {
        val d = ConsentDecision.Granted(version = current - 1, at = EPOCH)
        assertFalse(d.isAnalyticsGranted, "below-current grant must not enable tracking")
        assertTrue(d.needsDecision, "schema bumped => re-consent")
    }

    @Test
    fun stale_deny_needs_reconsent() {
        val d = ConsentDecision.Denied(version = current - 1, at = EPOCH)
        assertFalse(d.isAnalyticsGranted)
        assertTrue(d.needsDecision, "a decline under an older schema must be re-asked")
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
