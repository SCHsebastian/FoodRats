package es.schsebastian.foodrats.core.domain.analytics

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The user's analytics-consent decision. Sealed (mirrors the domain-error convention) so gating is
 * exhaustive. Persisted local-first (DataStore) so the gate works before any network. NOT itself an
 * analytics event.
 *
 * `Granted`/`Denied` carry the consent-schema [version] in effect when the choice was made; a stored
 * grant below [AnalyticsConfig.CURRENT_CONSENT_VERSION] is treated as [Unknown] (re-consent needed) —
 * see [isAnalyticsGranted].
 */
sealed interface ConsentDecision {
    data object Unknown : ConsentDecision
    data class Granted(val version: Int, val at: Instant) : ConsentDecision
    data class Denied(val version: Int, val at: Instant) : ConsentDecision
}

/** True only for a current-version explicit grant. The single predicate the analytics gate trusts. */
val ConsentDecision.isAnalyticsGranted: Boolean
    get() = this is ConsentDecision.Granted && version >= AnalyticsConfig.CURRENT_CONSENT_VERSION

/**
 * True when the routing gate must show the consent screen: no decision yet ([ConsentDecision.Unknown])
 * OR a stored grant/deny made under an older consent schema ([AnalyticsConfig.CURRENT_CONSENT_VERSION]
 * bumped → re-consent, GDPR purpose-specificity). The single predicate the consent-routing gate trusts —
 * do NOT confuse with [isAnalyticsGranted]: a *current-version* [ConsentDecision.Denied] is a settled
 * decision (gate stays away) yet is still "not granted" for tracking purposes.
 */
val ConsentDecision.needsDecision: Boolean
    get() = when (this) {
        is ConsentDecision.Unknown -> true
        is ConsentDecision.Granted -> version < AnalyticsConfig.CURRENT_CONSENT_VERSION
        is ConsentDecision.Denied -> version < AnalyticsConfig.CURRENT_CONSENT_VERSION
    }

/**
 * Reads and writes the analytics-consent decision. Implemented in `:core:data` over `AppPreferences`
 * (DataStore). The consent UI calls [grant]/[deny]; account-deletion / settings-revoke call
 * [revoke]. The `ConsentGatedAnalytics` decorator observes [decision] to flip the SDK on/off.
 */
interface ConsentPort {
    val decision: Flow<ConsentDecision>

    /** Records affirmative opt-in at [AnalyticsConfig.CURRENT_CONSENT_VERSION] with the current time. */
    suspend fun grant()

    /** Records an explicit decline at the current consent version. */
    suspend fun deny()

    /** Clears any prior grant back to a declined state (e.g. on account deletion or settings opt-out). */
    suspend fun revoke()
}
