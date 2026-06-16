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
}

/** How the weekly-recap story was reached (roadmap §2.4). */
enum class DigestStorySource(val wire: String) {
    NOTIFICATION("notification"),
    IN_APP("in_app"),
}
