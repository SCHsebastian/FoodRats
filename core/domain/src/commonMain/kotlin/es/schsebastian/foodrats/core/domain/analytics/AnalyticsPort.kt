package es.schsebastian.foodrats.core.domain.analytics

import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * The single cross-cutting analytics capability port — the only analytics surface feature code sees.
 * Mirrors [es.schsebastian.foodrats.core.domain.telemetry.CrashReporter]: declared here (vendor-free),
 * implemented per platform in `:core:data` (Firebase on Android, Swift bridge on iOS), bound per
 * platform in Koin (never in `coreDataModule`).
 *
 * All methods are NON-suspending and fire-and-forget — `logEvent` is batched by the SDK, so analytics
 * never consumes the "one `withContext(io)` per data-layer method" budget and is safe to call from a
 * ViewModel after a use case returns `Ok`.
 *
 * Consent is enforced by the `ConsentGatedAnalytics` decorator wrapping the real implementation, NOT
 * by call sites: when consent is not granted, [track]/[setUserProperty] are no-ops and [setUserId]
 * is forced to `null`.
 */
interface AnalyticsPort {
    /** Records a typed event. No-op (in the decorator) until consent is granted. */
    fun track(event: AnalyticsEvent)

    /** Associates subsequent events with the signed-in account UID, or clears it on sign-out. */
    fun setUserId(accountId: AccountId?)

    /** Sets a user-scoped property (the latest value sticks to future events). */
    fun setUserProperty(property: UserProperty, value: String)

    /** Applies the consent decision to the SDK (collection toggle + GA4 Consent Mode). */
    fun applyConsent(granted: Boolean)

    /** Clears all locally-cached analytics state (called on consent revoke / sign-out reset). */
    fun resetData()
}

/** No-op tracker — the debug-build binding and the default for ViewModels/previews/tests. */
object NoopAnalyticsTracker : AnalyticsPort {
    override fun track(event: AnalyticsEvent) = Unit
    override fun setUserId(accountId: AccountId?) = Unit
    override fun setUserProperty(property: UserProperty, value: String) = Unit
    override fun applyConsent(granted: Boolean) = Unit
    override fun resetData() = Unit
}
