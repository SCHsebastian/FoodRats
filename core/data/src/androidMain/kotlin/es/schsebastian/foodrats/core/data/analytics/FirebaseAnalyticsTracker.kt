package es.schsebastian.foodrats.core.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsValue
import es.schsebastian.foodrats.core.domain.analytics.UserProperty
import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * [AnalyticsPort] backed by the native Firebase Analytics (GA4) SDK — the ONLY place Firebase and the
 * GA4 limits appear. Like `AndroidCrashReporter`, it's bound from the Android app bootstrap, never in
 * `coreDataModule`. Every typed event/param is lowered onto a [Bundle] here and clamped to the GA4
 * caps (GA4 silently drops over-limit data, so we truncate rather than risk a silent loss).
 *
 * Wrapped by `ConsentGatedAnalytics`, so [track]/[setUserProperty] are only ever reached with consent
 * granted; [applyConsent] still drives the SDK collection toggle + Consent Mode for defense in depth.
 *
 * The [FirebaseAnalytics] instance is resolved lazily (via [provideAnalytics], on first use) rather than
 * at Koin graph resolution: `FirebaseAnalytics.getInstance(context)` is startup-cost we don't want to pay
 * during `startKoin`. The first port call happens only post-consent, so the SDK spins up just in time.
 */
internal class FirebaseAnalyticsTracker(
    provideAnalytics: () -> FirebaseAnalytics,
) : AnalyticsPort {

    private val analytics: FirebaseAnalytics by lazy(provideAnalytics)

    override fun track(event: AnalyticsEvent) {
        val bundle = Bundle()
        event.params.entries.take(AnalyticsConfig.MAX_PARAMS_PER_EVENT).forEach { (rawKey, value) ->
            val key = rawKey.take(AnalyticsConfig.MAX_PARAM_NAME_LENGTH)
            when (value) {
                is AnalyticsValue.Text ->
                    bundle.putString(key, value.value.take(AnalyticsConfig.MAX_PARAM_VALUE_LENGTH))
                is AnalyticsValue.Count -> bundle.putLong(key, value.value)
                is AnalyticsValue.Decimal -> bundle.putDouble(key, value.value)
                // GA4 has no boolean type — send as a legible "true"/"false" string.
                is AnalyticsValue.Flag -> bundle.putString(key, if (value.value) "true" else "false")
            }
        }
        analytics.logEvent(event.name.take(AnalyticsConfig.MAX_EVENT_NAME_LENGTH), bundle)
    }

    override fun setUserId(accountId: AccountId?) {
        analytics.setUserId(accountId?.value)
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        analytics.setUserProperty(
            property.key.take(AnalyticsConfig.MAX_USER_PROPERTY_NAME_LENGTH),
            value.take(AnalyticsConfig.MAX_USER_PROPERTY_VALUE_LENGTH),
        )
    }

    override fun applyConsent(granted: Boolean) {
        val status = if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        analytics.setConsent(mapOf(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status))
        analytics.setAnalyticsCollectionEnabled(granted)
    }

    override fun resetData() {
        analytics.resetAnalyticsData()
    }
}
