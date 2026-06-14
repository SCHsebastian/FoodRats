package es.schsebastian.foodrats.core.data.analytics

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.analytics.UserProperty
import es.schsebastian.foodrats.core.domain.analytics.isAnalyticsGranted
import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.concurrent.Volatile

/**
 * The single consent choke point. Wraps the real per-platform [AnalyticsPort] and forwards calls ONLY
 * while consent is granted; every call site stays consent-unaware (principle #3/#8). It observes
 * [ConsentPort.decision] on an app-lifetime scope, caches the latest into a volatile flag (so the
 * non-suspending [track] can check it synchronously), and pushes each change into the SDK
 * ([applyConsent] / [resetData]) — the GDPR/Consent-Mode transition.
 */
class ConsentGatedAnalytics(
    private val delegate: AnalyticsPort,
    consent: ConsentPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AnalyticsPort {

    @Volatile
    private var granted: Boolean = false

    init {
        consent.decision
            .map { it.isAnalyticsGranted }
            .distinctUntilChanged()
            .onEach { isGranted ->
                granted = isGranted
                delegate.applyConsent(isGranted)
                // On revoke/decline, wipe locally-cached analytics state so a fresh anonymous
                // identity starts next time consent is granted.
                if (!isGranted) delegate.resetData()
            }
            .launchIn(scope)
    }

    override fun track(event: AnalyticsEvent) {
        if (granted) delegate.track(event)
    }

    override fun setUserId(accountId: AccountId?) {
        // Stay anonymous (null) until consent is granted, even if a session exists.
        delegate.setUserId(if (granted) accountId else null)
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        if (granted) delegate.setUserProperty(property, value)
    }

    override fun applyConsent(granted: Boolean) = delegate.applyConsent(granted)

    override fun resetData() = delegate.resetData()
}
