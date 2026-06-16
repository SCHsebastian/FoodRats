package es.schsebastian.foodrats.app.consent

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel

/**
 * First-run analytics-consent gate. Writes the user's decision through [ConsentPort]; the
 * `ConsentGatedAnalytics` decorator flips the SDK on/off by OBSERVING `ConsentPort.decision`, and
 * `RootNavViewModel` advances past `NeedsConsent` the same way — so this VM neither calls
 * `AnalyticsPort.applyConsent` nor navigates itself. It only records the decision and (on grant) emits
 * the `consent_granted` event AFTER the grant lands, per the analytics charter rule (call site in the
 * VM, never a use case; recorded only once tracking is allowed).
 */
class ConsentViewModel(
    private val consent: ConsentPort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<ConsentState, ConsentIntent, ConsentEffect>(ConsentState()) {

    override suspend fun handle(intent: ConsentIntent) {
        if (currentState.isSubmitting) return
        update { it.copy(isSubmitting = true) }
        when (intent) {
            ConsentIntent.Grant -> {
                consent.grant()
                analytics.track(AnalyticsEvent.ConsentGranted(AnalyticsConfig.CURRENT_CONSENT_VERSION))
            }
            ConsentIntent.Deny -> consent.deny()
        }
        update { it.copy(isSubmitting = false) }
        emit(ConsentEffect.Decided)
    }
}
