package es.schsebastian.foodrats.core.data.di

import es.schsebastian.foodrats.core.data.analytics.ConsentGatedAnalytics
import es.schsebastian.foodrats.core.data.analytics.IosAnalyticsTracker
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import org.koin.dsl.module

/**
 * iOS-side Koin module registering the Firebase-Analytics-backed [AnalyticsPort], consent-gated. The
 * Swift caller in ContentView.swift supplies the lambdas at startup (see iosApp/AnalyticsBridge.swift).
 * Bound per platform, never in `coreDataModule` — same rule as `crashIosModule`. Collection is
 * disabled in debug on the Swift side (`#if DEBUG`), mirroring Crashlytics.
 */
fun analyticsIosModule(
    logEvent: (name: String, params: Map<String, Any>) -> Unit,
    setUserId: (accountId: String?) -> Unit,
    setUserProperty: (name: String, value: String) -> Unit,
    setConsent: (granted: Boolean) -> Unit,
    reset: () -> Unit,
) = module {
    single<AnalyticsPort> {
        ConsentGatedAnalytics(
            delegate = IosAnalyticsTracker(logEvent, setUserId, setUserProperty, setConsent, reset),
            consent = get(),
        )
    }
}
