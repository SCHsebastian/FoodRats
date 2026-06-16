package es.schsebastian.foodrats.core.data.analytics

import es.schsebastian.foodrats.core.domain.analytics.AnalyticsConfig
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsValue
import es.schsebastian.foodrats.core.domain.analytics.UserProperty
import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * [AnalyticsPort] for iOS. The native Firebase Analytics SDK is resolved via SPM inside Xcode and is
 * invisible to Gradle/Kotlin — so, exactly like `IosCrashReporter`, we bridge through Swift lambdas
 * supplied at app startup (see iosApp/AnalyticsBridge.swift + MainViewController). Params are lowered
 * to a `Map<String, Any>` that bridges to a Swift `[String: Any]`.
 */
class IosAnalyticsTracker(
    private val logEventBridge: (name: String, params: Map<String, Any>) -> Unit,
    private val setUserIdBridge: (accountId: String?) -> Unit,
    private val setUserPropertyBridge: (name: String, value: String) -> Unit,
    private val setConsentBridge: (granted: Boolean) -> Unit,
    private val resetBridge: () -> Unit,
) : AnalyticsPort {

    override fun track(event: AnalyticsEvent) {
        val params = HashMap<String, Any>()
        event.params.entries.take(AnalyticsConfig.MAX_PARAMS_PER_EVENT).forEach { (rawKey, value) ->
            val key = rawKey.take(AnalyticsConfig.MAX_PARAM_NAME_LENGTH)
            params[key] = when (value) {
                is AnalyticsValue.Text -> value.value.take(AnalyticsConfig.MAX_PARAM_VALUE_LENGTH)
                is AnalyticsValue.Count -> value.value
                is AnalyticsValue.Decimal -> value.value
                is AnalyticsValue.Flag -> if (value.value) "true" else "false"
            }
        }
        logEventBridge(event.name.take(AnalyticsConfig.MAX_EVENT_NAME_LENGTH), params)
    }

    override fun setUserId(accountId: AccountId?) {
        setUserIdBridge(accountId?.value)
    }

    override fun setUserProperty(property: UserProperty, value: String) {
        setUserPropertyBridge(
            property.key.take(AnalyticsConfig.MAX_USER_PROPERTY_NAME_LENGTH),
            value.take(AnalyticsConfig.MAX_USER_PROPERTY_VALUE_LENGTH),
        )
    }

    override fun applyConsent(granted: Boolean) {
        setConsentBridge(granted)
    }

    override fun resetData() {
        resetBridge()
    }
}
