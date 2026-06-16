package es.schsebastian.foodrats.core.data.analytics

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Centralizes analytics identity: observes the session and pushes the account UID to [AnalyticsPort]
 * (cleared to `null` on sign-out). Bound eagerly (`createdAtStart`) so identity tracks the session
 * for the whole app lifetime without any feature needing to know about it. The actual `setUserId` is
 * still consent-gated by `ConsentGatedAnalytics`, so this binder can naively forward the UID.
 */
class AnalyticsIdentityBinder(
    session: SessionProvider,
    analytics: AnalyticsPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    init {
        session.current
            .map { it?.accountId }
            .distinctUntilChanged()
            .onEach { accountId -> analytics.setUserId(accountId) }
            .launchIn(scope)
    }
}
