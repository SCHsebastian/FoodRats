package es.schsebastian.foodrats.core.domain.analytics

/**
 * User-scoped properties (GA4 "user properties"). Set via [AnalyticsPort.setUserProperty]; the
 * latest value sticks to all subsequent events for the identified user. Names are ≤24 chars and
 * values ≤36 chars (GA4 limits). Never PII — these are crew/locale/permission slices for cohorting.
 */
enum class UserProperty(val key: String) {
    ACTIVE_CREW_SIZE("active_crew_size"),
    CREWS_COUNT("crews_count"),
    APP_LOCALE("app_locale"),
    NOTIF_PERMISSION_STATE("notif_permission_state"),
    DATA_CONSENT_VERSION("data_consent_version"),
}
