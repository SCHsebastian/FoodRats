package es.schsebastian.foodrats.feature.notifications.data.push

import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload

/**
 * Parses an incoming FCM `data` map (always `Map<String, String>` on both platforms) into a
 * domain [Reminder]. The server-side `key` field selects the parsing branch; unknown or missing
 * keys return null — caller treats null as "ignore this push".
 */
class PushPayloadMapper(private val clock: Clock) {

    fun toReminder(data: Map<String, String>): Reminder? {
        val key = data["key"] ?: return null
        return when (key) {
            KEY_NEW_COMMENT -> newComment(data)
            KEY_NEW_MEAL_POST -> newMealPost(data)
            KEY_WEEKLY_DIGEST -> weeklyDigest(data)
            else -> null
        }
    }

    private fun newComment(d: Map<String, String>): Reminder? {
        val crewId = d["crewId"] ?: return null
        val mealId = d["mealId"] ?: return null
        val commentId = d["commentId"] ?: return null
        val commenterName = d["commenterName"].orEmpty()
        val dishName = d["dishName"].orEmpty()
        return Reminder(
            id = commentId,
            kind = ReminderKind.NewComment,
            deliverAt = clock.now(),
            title = "$commenterName commented on your $dishName",
            body = "Tap to read",
            payload = ReminderPayload.Comment(crewId, mealId, commentId),
        )
    }

    private fun newMealPost(d: Map<String, String>): Reminder? {
        val crewId = d["crewId"] ?: return null
        val mealId = d["mealId"] ?: return null
        val authorName = d["authorName"].orEmpty()
        val dishName = d["dishName"].orEmpty()
        return Reminder(
            id = mealId,
            kind = ReminderKind.NewMealPost,
            deliverAt = clock.now(),
            title = "$authorName posted a meal",
            body = "$dishName — tap to view",
            payload = ReminderPayload.Meal(crewId, mealId),
        )
    }

    private fun weeklyDigest(d: Map<String, String>): Reminder? {
        val crewId = d["crewId"] ?: return null
        val weekStartIso = d["weekStartIso"] ?: return null
        val parts = buildList {
            d["bestMealDishName"]?.let { dish ->
                val score = d["bestMealScore"]
                add(if (score != null) "Best meal: $dish ($score★)" else "Best meal: $dish")
            }
            d["bestCookName"]?.let { add("Best cook: $it") }
            d["mostProlificName"]?.let { name ->
                val count = d["mostProlificCount"]
                add(if (count != null) "Most prolific: $name ($count)" else "Most prolific: $name")
            }
            d["mostVotedDishName"]?.let { add("Most voted: $it") }
            d["mostCriticizedName"]?.let { add("Most criticized: $it") }
        }
        return Reminder(
            id = "weekly-$weekStartIso",
            kind = ReminderKind.WeeklyDigest,
            deliverAt = clock.now(),
            title = "Your week in food",
            body = parts.joinToString(" · "),
            payload = ReminderPayload.WeeklyDigest(crewId, weekStartIso),
        )
    }

    private companion object {
        const val KEY_NEW_COMMENT = "new_comment"
        const val KEY_NEW_MEAL_POST = "new_meal_post"
        const val KEY_WEEKLY_DIGEST = "weekly_digest"
    }
}
