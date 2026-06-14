package es.schsebastian.foodrats.core.domain.analytics

/**
 * Hard constraints the taxonomy and adapters must respect. GA4/Firebase silently DROPS over-limit
 * data, so the adapter truncates to these and [AnalyticsTaxonomyTest] asserts every event name
 * conforms. Numbers are from the Google Analytics 4 / Firebase event-and-property limits docs.
 */
object AnalyticsConfig {
    /**
     * Consent schema version. Bumping this drops any stored grant with a lower version back to
     * [ConsentDecision.Unknown], forcing re-consent (GDPR purpose-specificity).
     */
    const val CURRENT_CONSENT_VERSION: Int = 1

    /** A name starting with any of these is rejected by GA4. */
    val RESERVED_EVENT_PREFIXES: List<String> = listOf("firebase_", "google_", "ga_")

    /**
     * Auto-collected event names we must NOT redefine. `screen_view` is deliberately ABSENT: with
     * Compose Multiplatform navigation, Firebase does not auto-track screen views, so emitting
     * `screen_view` via the official manual API ([AnalyticsEvent.ScreenViewed]) is the sanctioned
     * path, not a redefinition.
     */
    val AUTO_COLLECTED_NAMES: Set<String> = setOf(
        "first_open", "session_start", "user_engagement", "app_remove", "app_update",
        "os_update", "app_clear_data", "app_exception", "notification_receive",
        "notification_open", "notification_dismiss", "notification_foreground", "in_app_purchase",
    )

    /** `[a-z][a-z0-9_]{0,39}` — letter-first, snake_case, ≤40. */
    val EVENT_NAME_REGEX: Regex = Regex("^[a-z][a-z0-9_]{0,39}$")

    const val MAX_EVENT_NAME_LENGTH: Int = 40
    const val MAX_PARAMS_PER_EVENT: Int = 25
    const val MAX_PARAM_NAME_LENGTH: Int = 40
    const val MAX_PARAM_VALUE_LENGTH: Int = 100
    const val MAX_USER_PROPERTIES: Int = 25
    const val MAX_USER_PROPERTY_NAME_LENGTH: Int = 24
    const val MAX_USER_PROPERTY_VALUE_LENGTH: Int = 36
}
