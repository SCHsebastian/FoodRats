package es.schsebastian.foodrats.feature.notifications.domain.model

/**
 * Categorizes a reminder so the UI can pick an icon, channel, and (eventually) deep-link target.
 * `StreakAtRisk` is the only locally-scheduled kind; the rest are server-driven via FCM.
 */
enum class ReminderKind {
    StreakAtRisk,
    NewComment,
    NewMealPost,
    WeeklyDigest,
}
