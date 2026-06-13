@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package es.schsebastian.foodrats.feature.notifications.data.push

import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.jetbrains.compose.resources.getString

/**
 * Parses an incoming FCM `data` map (always `Map<String, String>` on both platforms) into a
 * domain [Reminder]. The server-side `key` field selects the parsing branch; unknown or missing
 * keys return null — caller treats null as "ignore this push".
 *
 * Parsing ([parse]) is pure and unit-tested. Display text ([toReminder]) is resolved through
 * [NotificationStringKey] via the suspending Compose-resources `getString(...)` API — same
 * discipline as [es.schsebastian.foodrats.feature.notifications.data.adapter.StreakNotificationAdapter]
 * — so every client-built push string honours the active locale (en/es) instead of being hardcoded
 * English in the data layer.
 */
class PushPayloadMapper(private val clock: Clock) {

    /** Structured, locale-free result of parsing an FCM `data` map. Unit-testable without resources. */
    sealed interface PushContent {
        val id: String
        val kind: ReminderKind
        val payload: ReminderPayload

        data class NewComment(
            override val id: String,
            val commenterName: String,
            val dishName: String,
            override val payload: ReminderPayload.Comment,
        ) : PushContent {
            override val kind get() = ReminderKind.NewComment
        }

        data class NewMealPost(
            override val id: String,
            val authorName: String,
            val dishName: String,
            override val payload: ReminderPayload.Meal,
        ) : PushContent {
            override val kind get() = ReminderKind.NewMealPost
        }

        data class WeeklyDigest(
            override val id: String,
            override val payload: ReminderPayload.WeeklyDigest,
        ) : PushContent {
            override val kind get() = ReminderKind.WeeklyDigest
        }
    }

    /**
     * Pure parse — no resource lookup, no clock. Returns null for unknown / malformed payloads.
     * Kept separate from [toReminder] so the branching logic is unit-testable in `commonTest`,
     * where bundled Compose Resources are not available.
     */
    fun parse(data: Map<String, String>): PushContent? {
        val key = data["key"] ?: return null
        return when (key) {
            KEY_NEW_COMMENT -> newComment(data)
            KEY_NEW_MEAL_POST -> newMealPost(data)
            KEY_WEEKLY_DIGEST -> weeklyDigest(data)
            else -> null
        }
    }

    /**
     * Parse [data] and resolve its title/body through [NotificationStringKey] for the active locale.
     * Suspends because `getString(...)` is suspending. Returns null for unknown / malformed payloads.
     */
    suspend fun toReminder(data: Map<String, String>): Reminder? {
        val content = parse(data) ?: return null
        return when (content) {
            is PushContent.NewComment -> Reminder(
                id = content.id,
                kind = content.kind,
                deliverAt = clock.now(),
                title = getString(
                    NotificationStringKey.NewCommentTitle.resourceId,
                    content.commenterName,
                    content.dishName,
                ),
                body = getString(NotificationStringKey.NewCommentBody.resourceId),
                payload = content.payload,
            )
            is PushContent.NewMealPost -> Reminder(
                id = content.id,
                kind = content.kind,
                deliverAt = clock.now(),
                title = getString(NotificationStringKey.NewMealPostTitle.resourceId, content.authorName),
                body = getString(NotificationStringKey.NewMealPostBody.resourceId, content.dishName),
                payload = content.payload,
            )
            is PushContent.WeeklyDigest -> Reminder(
                id = content.id,
                kind = content.kind,
                deliverAt = clock.now(),
                title = getString(NotificationStringKey.WeeklyDigestTitle.resourceId),
                body = getString(NotificationStringKey.WeeklyDigestBody.resourceId),
                payload = content.payload,
            )
        }
    }

    private fun newComment(d: Map<String, String>): PushContent? {
        val crewId = d["crewId"] ?: return null
        val mealId = d["mealId"] ?: return null
        val commentId = d["commentId"] ?: return null
        return PushContent.NewComment(
            id = commentId,
            commenterName = d["commenterName"].orEmpty(),
            dishName = d["dishName"].orEmpty(),
            payload = ReminderPayload.Comment(crewId, mealId, commentId),
        )
    }

    private fun newMealPost(d: Map<String, String>): PushContent? {
        val crewId = d["crewId"] ?: return null
        val mealId = d["mealId"] ?: return null
        return PushContent.NewMealPost(
            id = mealId,
            authorName = d["authorName"].orEmpty(),
            dishName = d["dishName"].orEmpty(),
            payload = ReminderPayload.Meal(crewId, mealId),
        )
    }

    private fun weeklyDigest(d: Map<String, String>): PushContent? {
        val crewId = d["crewId"] ?: return null
        val weekStartIso = d["weekStartIso"] ?: return null
        return PushContent.WeeklyDigest(
            id = "weekly-$weekStartIso",
            payload = ReminderPayload.WeeklyDigest(crewId, weekStartIso),
        )
    }

    private companion object {
        const val KEY_NEW_COMMENT = "new_comment"
        const val KEY_NEW_MEAL_POST = "new_meal_post"
        const val KEY_WEEKLY_DIGEST = "weekly_digest"
    }
}
