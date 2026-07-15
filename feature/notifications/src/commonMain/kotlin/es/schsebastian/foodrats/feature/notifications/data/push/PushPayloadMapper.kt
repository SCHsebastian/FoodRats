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

        /**
         * Fires only when a comment @-mentions the recipient (see PLAN.md — "No double
         * notification"). Reuses [ReminderPayload.Comment] so the tap deep-links identically to
         * [NewComment].
         */
        data class CommentMention(
            override val id: String,
            val commenterName: String,
            val dishName: String,
            override val payload: ReminderPayload.Comment,
        ) : PushContent {
            override val kind get() = ReminderKind.CommentMention
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

        /**
         * The server-side social-proof streak nudge ("N of M crewmates posted today"). It carries
         * no crew/meal id, so its [payload] is [ReminderPayload.None] — a tap just opens the app to
         * Feed, matching the weekly-digest / inactivity "no deep target" convention.
         */
        data class SocialNudge(
            override val id: String,
            val postedCount: Int,
            val crewSize: Int,
        ) : PushContent {
            override val kind get() = ReminderKind.SocialNudge
            override val payload get() = ReminderPayload.None
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
            KEY_COMMENT_MENTION -> commentMention(data)
            KEY_NEW_MEAL_POST -> newMealPost(data)
            KEY_WEEKLY_DIGEST -> weeklyDigest(data)
            KEY_SOCIAL_NUDGE -> socialNudge(data)
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
            is PushContent.CommentMention -> Reminder(
                id = content.id,
                kind = content.kind,
                deliverAt = clock.now(),
                title = getString(
                    NotificationStringKey.CommentMentionTitle.resourceId,
                    content.commenterName,
                    content.dishName,
                ),
                body = getString(NotificationStringKey.CommentMentionBody.resourceId),
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
            is PushContent.SocialNudge -> Reminder(
                id = content.id,
                kind = content.kind,
                deliverAt = clock.now(),
                title = getString(NotificationStringKey.SocialNudgeTitle.resourceId),
                body = getString(
                    NotificationStringKey.SocialNudgeBody.resourceId,
                    content.postedCount,
                    content.crewSize,
                ),
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

    private fun commentMention(d: Map<String, String>): PushContent? {
        val crewId = d["crewId"] ?: return null
        val mealId = d["mealId"] ?: return null
        val commentId = d["commentId"] ?: return null
        return PushContent.CommentMention(
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

    /**
     * The server sends `postedCount`/`crewSize` as strings. Both must parse to ints; a malformed
     * or absent count makes the body untemplatable, so we return null (push ignored in-app). The
     * nudge has no per-send id in the contract, so the id is fixed — a same-day re-send replaces
     * the prior banner rather than stacking.
     */
    private fun socialNudge(d: Map<String, String>): PushContent? {
        val postedCount = d["postedCount"]?.toIntOrNull() ?: return null
        val crewSize = d["crewSize"]?.toIntOrNull() ?: return null
        return PushContent.SocialNudge(
            id = "social-nudge",
            postedCount = postedCount,
            crewSize = crewSize,
        )
    }

    private companion object {
        const val KEY_NEW_COMMENT = "new_comment"

        /** Discriminator for the comment @-mention push (server const `KEY_COMMENT_MENTION`). */
        const val KEY_COMMENT_MENTION = "comment_mention"
        const val KEY_NEW_MEAL_POST = "new_meal_post"
        const val KEY_WEEKLY_DIGEST = "weekly_digest"

        /** Discriminator for the server-side social-proof streak nudge (server const `KEY_SOCIAL_NUDGE`). */
        const val KEY_SOCIAL_NUDGE = "social_nudge"
    }
}
