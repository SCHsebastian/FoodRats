package es.schsebastian.foodrats.core.domain.analytics

/**
 * Closed value sets for analytics event properties. Each carries its canonical lowercase [wire]
 * slug — the only string that crosses to GA4. Feature ViewModels map their own domain enums onto
 * these via an exhaustive `when`, so a new domain variant forces a compile error here rather than
 * silently emitting an unmapped value. Analytics owns its dimension vocabulary; the domain stays
 * uncoupled (a feature `MealSlot` enum is never imported into `:core:domain`).
 */
enum class AuthMethod(val wire: String) {
    GOOGLE("google"),
    APPLE("apple"),
    EMAIL("email"),
}

enum class JoinMethod(val wire: String) {
    INVITE_CODE("invite_code"),
    INVITE_LINK("invite_link"),
}

// Meal slot is the domain type `core.domain.meal.MealSlot`; events emit `slot.key()` directly rather
// than duplicating the enum here.

enum class PublishSource(val wire: String) {
    CAMERA("camera"),
    GALLERY("gallery"),
    UNKNOWN("unknown"),
}

enum class CaptureSource(val wire: String) {
    CAMERA("camera"),
    GALLERY("gallery"),
    UNKNOWN("unknown"),
}

/**
 * The user-changeable persisted preferences, collapsed under one `setting_changed` event instead of
 * a leaf-per-toggle. Boolean toggles ([NOTIFICATIONS], [BLIND_VOTING]) carry their new value; the
 * others omit the target value on purpose (a locale tag could read as locale PII; the analytical
 * question is whether a retained user tunes a setting, not to what).
 */
enum class AppSetting(val wire: String) {
    THEME("theme"),
    LANGUAGE("language"),
    NOTIFICATIONS("notifications"),
    MEAL_REMINDERS("meal_reminders"),
    BLIND_VOTING("blind_voting"),
}

/** How the weekly-recap story was reached (roadmap §2.4). */
enum class DigestStorySource(val wire: String) {
    NOTIFICATION("notification"),
    IN_APP("in_app"),
}
