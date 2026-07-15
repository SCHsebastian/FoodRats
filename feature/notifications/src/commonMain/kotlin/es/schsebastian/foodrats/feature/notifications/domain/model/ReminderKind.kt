package es.schsebastian.foodrats.feature.notifications.domain.model

/**
 * Categorizes a reminder so the UI can pick an icon, channel, and (eventually) deep-link target.
 * `StreakAtRisk` is the only locally-scheduled kind; the rest are server-driven via FCM.
 * `SocialNudge` is the server-side social-proof streak nudge ("N of M crewmates posted today");
 * it carries no deep target — a tap just opens the app to Feed.
 * `CommentMention` fires only on comment CREATE when the commenter @-mentions a crew member;
 * it reuses the same deep link as `NewComment` (`ReminderPayload.Comment`).
 */
enum class ReminderKind {
    StreakAtRisk,
    NewComment,
    NewMealPost,
    WeeklyDigest,
    SocialNudge,
    CommentMention,
}
