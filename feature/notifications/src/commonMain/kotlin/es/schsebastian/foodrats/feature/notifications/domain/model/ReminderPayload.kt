package es.schsebastian.foodrats.feature.notifications.domain.model

/**
 * Optional structured payload carried by a [Reminder]. Used to deep-link from the notification tap
 * into a specific destination. Parsed in v1 but not consumed yet.
 */
sealed interface ReminderPayload {
    data object None : ReminderPayload
    data class Comment(
        val crewId: String,
        val mealId: String,
        val commentId: String,
    ) : ReminderPayload
    data class Meal(
        val crewId: String,
        val mealId: String,
    ) : ReminderPayload
    data class WeeklyDigest(
        val crewId: String,
        val weekStartIso: String,
    ) : ReminderPayload
}
