package es.schsebastian.foodrats.core.domain.analytics

/**
 * A typed analytics parameter value. Keeps `Map<String, Any>` out of call sites: an event leaf
 * declares each param as one of these variants, and the per-platform adapter is the only place that
 * lowers them onto the vendor's primitive bundle (Firebase `Bundle.putString/putLong/putDouble`).
 *
 * GA4 accepts only string / number params; booleans are sent as the strings `"true"`/`"false"` at
 * the adapter so they stay legible in the GA4 UI.
 */
sealed interface AnalyticsValue {
    data class Text(val value: String) : AnalyticsValue
    data class Count(val value: Long) : AnalyticsValue
    data class Decimal(val value: Double) : AnalyticsValue
    data class Flag(val value: Boolean) : AnalyticsValue
}
