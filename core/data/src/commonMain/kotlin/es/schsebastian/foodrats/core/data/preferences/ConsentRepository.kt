package es.schsebastian.foodrats.core.data.preferences

import es.schsebastian.foodrats.core.data.datastore.AppPreferences
import es.schsebastian.foodrats.core.data.datastore.Keys
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.ConsentDecision
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * [ConsentPort] over DataStore (`AppPreferences`). Local-first so the analytics gate works before any
 * network. Absence of a stored state = [ConsentDecision.Unknown] → analytics stays a hard no-op.
 */
class ConsentRepository(
    private val prefs: AppPreferences,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ConsentPort {

    override val decision: Flow<ConsentDecision> = combine(
        prefs.observe(Keys.AnalyticsConsentState),
        prefs.observe(Keys.AnalyticsConsentVersion),
        prefs.observe(Keys.AnalyticsConsentDecidedAt),
    ) { state, version, decidedAtMs ->
        val at = Instant.fromEpochMilliseconds(decidedAtMs ?: 0L)
        when (state) {
            STATE_GRANTED -> ConsentDecision.Granted(version ?: 0, at)
            STATE_DENIED  -> ConsentDecision.Denied(version ?: 0, at)
            else          -> ConsentDecision.Unknown
        }
    }

    override suspend fun grant() = write(STATE_GRANTED)
    override suspend fun deny() = write(STATE_DENIED)
    override suspend fun revoke() = write(STATE_DENIED)

    private suspend fun write(state: String): Unit = withContext(dispatchers.io) {
        prefs.set(Keys.AnalyticsConsentState, state)
        prefs.set(Keys.AnalyticsConsentVersion, AnalyticsConfig.CURRENT_CONSENT_VERSION)
        prefs.set(Keys.AnalyticsConsentDecidedAt, clock.now().toEpochMilliseconds())
    }

    private companion object {
        const val STATE_GRANTED = "granted"
        const val STATE_DENIED = "denied"
    }
}
