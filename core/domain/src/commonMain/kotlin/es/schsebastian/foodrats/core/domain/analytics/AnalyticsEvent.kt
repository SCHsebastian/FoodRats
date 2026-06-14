package es.schsebastian.foodrats.core.domain.analytics

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * The FoodRats analytics taxonomy — the single source of truth for product analytics, in executable
 * form. Every trackable moment is one leaf of this sealed interface, carrying its typed properties;
 * the leaf computes the snake_case wire [name] (GA4 ≤40 chars, letter-first) and its [params] map of
 * [AnalyticsValue]s. Call sites construct a leaf; the per-platform adapter is the ONLY place that
 * lowers it onto Firebase. Adding a property = a new optional field (additive-only, §7 of the spec);
 * renaming a name/param is breaking (expand-contract).
 *
 * Names reuse GA4 *predefined* event names where one fits (`login`, `sign_up`, `join_group`,
 * `share`, `select_content`, `post_score`) so GA4 auto-populates its predefined dimensions; custom
 * names cover the rest. See `docs/specs/2026-06-14-analytics-base-definition-design.md` §6.
 *
 * INVARIANT: no leaf may carry PII (email, display name, free text, precise location). Only UIDs,
 * enum slugs, counts, booleans, durations. Locked by [AnalyticsTaxonomyTest].
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, AnalyticsValue>

    // ───────────────────────────── auth ─────────────────────────────

    /** GA4 predefined `login`. Fired after a sign-in `Result` resolves `Ok`. */
    data class LoggedIn(val method: AuthMethod) : AnalyticsEvent {
        override val name = "login"
        override val params = mapOf("method" to text(method.wire))
    }

    /** GA4 predefined `sign_up`. Fired after a successful first-time account creation. */
    data class SignedUp(val method: AuthMethod) : AnalyticsEvent {
        override val name = "sign_up"
        override val params = mapOf("method" to text(method.wire))
    }

    /** Sign-in/sign-up `Result` resolved `Err`. [errorLeaf] is the sealed-error class name, never PII. */
    data class SignInFailed(val method: AuthMethod, val errorLeaf: String) : AnalyticsEvent {
        override val name = "sign_in_failed"
        override val params = mapOf("method" to text(method.wire), "error_leaf" to text(errorLeaf))
    }

    // ───────────────────────────── crew ─────────────────────────────

    /** GA4 predefined `join_group`. */
    data class CrewJoined(val crewId: CrewId, val method: JoinMethod) : AnalyticsEvent {
        override val name = "join_group"
        override val params = mapOf(
            "group_id" to text(crewId.value),
            "join_method" to text(method.wire),
        )
    }

    data class CrewCreated(val crewId: CrewId) : AnalyticsEvent {
        override val name = "crew_created"
        override val params = mapOf("crew_id" to text(crewId.value))
    }

    data class CrewLeft(val crewId: CrewId) : AnalyticsEvent {
        override val name = "crew_left"
        override val params = mapOf("crew_id" to text(crewId.value))
    }

    /** GA4 predefined `share`, content_type=crew_invite. */
    data class CrewInviteShared(val crewId: CrewId) : AnalyticsEvent {
        override val name = "share"
        override val params = mapOf(
            "content_type" to text("crew_invite"),
            "item_id" to text(crewId.value),
        )
    }

    // ─────────────────────── meal / publish funnel ───────────────────────

    data class MealCaptureStarted(val source: CaptureSource) : AnalyticsEvent {
        override val name = "meal_capture_started"
        override val params = mapOf("capture_source" to text(source.wire))
    }

    /** On-device classification completed (advisory — never gates the funnel). */
    data class PlateClassified(
        val detectedCount: Int,
        val latencyMs: Long,
        val classifierVersion: String,
    ) : AnalyticsEvent {
        override val name = "plate_classified"
        override val params = mapOf(
            "detected_count" to count(detectedCount),
            "classify_latency_ms" to AnalyticsValue.Count(latencyMs),
            "classifier_version" to text(classifierVersion),
        )
    }

    /** User confirmed the ingredient picker selection (measures the detected≠confirmed delta). */
    data class IngredientsConfirmed(
        val detectedCount: Int,
        val confirmedCount: Int,
    ) : AnalyticsEvent {
        override val name = "ingredients_confirmed"
        override val params = mapOf(
            "detected_count" to count(detectedCount),
            "confirmed_count" to count(confirmedCount),
        )
    }

    data object MealComposerOpened : AnalyticsEvent {
        override val name = "meal_composer_opened"
        override val params = emptyMap<String, AnalyticsValue>()
    }

    /** Publish enqueued (Result Ok). Multi-crew → [audienceCrewCount], no single crew_id. */
    data class MealPublished(
        val slot: MealSlot,
        val ingredientCount: Int,
        val hasDescription: Boolean,
        val audienceCrewCount: Int,
        val source: PublishSource,
    ) : AnalyticsEvent {
        override val name = "meal_published"
        override val params = mapOf(
            "meal_slot" to text(slot.key()),
            "ingredient_count" to count(ingredientCount),
            "has_description" to flag(hasDescription),
            "audience_crew_count" to count(audienceCrewCount),
            "publish_source" to text(source.wire),
        )
    }

    data class MealPublishFailed(val errorLeaf: String) : AnalyticsEvent {
        override val name = "meal_publish_failed"
        override val params = mapOf("error_leaf" to text(errorLeaf))
    }

    data class MealDeleted(val byAuthor: Boolean) : AnalyticsEvent {
        override val name = "meal_deleted"
        override val params = mapOf("by_author" to flag(byAuthor))
    }

    // ─────────────────────────── feed engagement ───────────────────────────

    /** GA4 predefined `select_content`, content_type=meal. Opening a meal's detail. */
    data class MealOpened(val mealId: MealId) : AnalyticsEvent {
        override val name = "select_content"
        override val params = mapOf(
            "content_type" to text("meal"),
            "item_id" to text(mealId.value),
        )
    }

    /** GA4 predefined `post_score`. Rating a crewmate's meal. */
    data class MealRated(val mealId: MealId, val score: Int) : AnalyticsEvent {
        override val name = "post_score"
        override val params = mapOf(
            "score" to count(score),
            "meal_id" to text(mealId.value),
        )
    }

    data class CommentPosted(val mealId: MealId) : AnalyticsEvent {
        override val name = "comment_posted"
        override val params = mapOf("meal_id" to text(mealId.value))
    }

    /** A day's feed loaded. [dayOffset] = days before today (0 = today). */
    data class FeedDayViewed(val mealCount: Int, val dayOffset: Int) : AnalyticsEvent {
        override val name = "feed_day_viewed"
        override val params = mapOf(
            "meal_count" to count(mealCount),
            "day_offset" to count(dayOffset),
        )
    }

    // ─────────────────────────────── stats ───────────────────────────────

    data object StreakViewed : AnalyticsEvent {
        override val name = "streak_viewed"
        override val params = emptyMap<String, AnalyticsValue>()
    }

    data object LeaderboardViewed : AnalyticsEvent {
        override val name = "leaderboard_viewed"
        override val params = emptyMap<String, AnalyticsValue>()
    }

    // ─────────────────────────── notifications ───────────────────────────

    data class NotifPermissionPrompted(val promptCount: Int) : AnalyticsEvent {
        override val name = "notif_permission_prompted"
        override val params = mapOf("prompt_count" to count(promptCount))
    }

    data object NotifPermissionGranted : AnalyticsEvent {
        override val name = "notif_permission_granted"
        override val params = emptyMap<String, AnalyticsValue>()
    }

    data object NotifPermissionDenied : AnalyticsEvent {
        override val name = "notif_permission_denied"
        override val params = emptyMap<String, AnalyticsValue>()
    }

    // ──────────────────────────── lifecycle ────────────────────────────

    /**
     * Manual `screen_view` (the sanctioned API under Compose nav, which Firebase does not
     * auto-track). [screen] is derived from the `Route` type, never the raw arg-bearing route string.
     */
    data class ScreenViewed(val screen: ScreenName) : AnalyticsEvent {
        override val name = "screen_view"
        override val params = mapOf("screen_name" to text(screen.wire))
    }

    // ───────────────────────────── consent ─────────────────────────────

    /** Recorded only AFTER the grant lands, so it is itself consent-compliant. */
    data class ConsentGranted(val version: Int) : AnalyticsEvent {
        override val name = "consent_granted"
        override val params = mapOf("consent_version" to count(version))
    }
}

private fun text(v: String): AnalyticsValue = AnalyticsValue.Text(v)
private fun count(v: Int): AnalyticsValue = AnalyticsValue.Count(v.toLong())
private fun flag(v: Boolean): AnalyticsValue = AnalyticsValue.Flag(v)
