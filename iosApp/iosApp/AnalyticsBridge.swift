import Foundation
import FirebaseAnalytics

/// Swift bridge over Firebase Analytics (GA4), mirroring `CrashlyticsBridge`.
///
/// FirebaseAnalytics is resolved via SPM inside Xcode and is invisible to Gradle/Kotlin, so the
/// shared module (`IosAnalyticsTracker`) calls into these static methods through lambdas wired in
/// ContentView.swift -> MainViewController. Consent gating happens in Kotlin
/// (`ConsentGatedAnalytics`); this layer just forwards to the SDK.
///
/// Debug isolation: collection is disabled in `#if DEBUG` so simulator/dev traffic never reaches the
/// production GA4 property (mirrors the Crashlytics debug behavior).
enum AnalyticsBridge {

    static func logEvent(name: String, params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }

    static func setUserId(_ accountId: String?) {
        Analytics.setUserID(accountId)
    }

    static func setUserProperty(name: String, value: String) {
        Analytics.setUserProperty(value, forName: name)
    }

    /// Applies the consent decision: GA4 Consent Mode `analyticsStorage` + the SDK collection toggle.
    static func setConsent(granted: Bool) {
        Analytics.setConsent([.analyticsStorage: granted ? .granted : .denied])
        #if DEBUG
        Analytics.setAnalyticsCollectionEnabled(false)
        #else
        Analytics.setAnalyticsCollectionEnabled(granted)
        #endif
    }

    static func resetData() {
        Analytics.resetAnalyticsData()
    }
}
