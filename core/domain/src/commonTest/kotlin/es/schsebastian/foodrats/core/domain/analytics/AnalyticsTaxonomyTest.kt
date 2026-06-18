package es.schsebastian.foodrats.core.domain.analytics

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.getOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Locks the analytics taxonomy against the GA4/Firebase wire contract and the no-PII invariant
 * BEFORE events ever reach DebugView. GA4 silently drops malformed events, so a violation here is
 * the only place it surfaces. Every [AnalyticsEvent] leaf must appear in [allEvents]; if you add a
 * leaf and forget it here, you lose coverage (the governance PR review is the backstop).
 */
class AnalyticsTaxonomyTest {

    private val crewId = CrewId.of("crew-1").getOrNull()!!
    private val mealId = MealId.of("meal-1").getOrNull()!!

    private val allEvents: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.LoggedIn(AuthMethod.GOOGLE),
        AnalyticsEvent.SignedUp(AuthMethod.EMAIL),
        AnalyticsEvent.SignInFailed(AuthMethod.GOOGLE, "UserCancelled"),
        AnalyticsEvent.CrewJoined(crewId, JoinMethod.INVITE_CODE),
        AnalyticsEvent.CrewCreated(crewId),
        AnalyticsEvent.CrewLeft(crewId),
        AnalyticsEvent.CrewMemberRemoved(crewId),
        AnalyticsEvent.CrewInviteShared(crewId),
        AnalyticsEvent.PlateShared(mealId),
        AnalyticsEvent.AwardShared(mealId),
        AnalyticsEvent.StreakShared(streakDays = 14),
        AnalyticsEvent.RecapShared(sceneKind = "your_week"),
        AnalyticsEvent.MealCaptureStarted(CaptureSource.CAMERA),
        AnalyticsEvent.PlateClassified(detectedCount = 3, latencyMs = 120L, classifierVersion = "food101_v1"),
        AnalyticsEvent.IngredientsConfirmed(detectedCount = 3, confirmedCount = 2),
        AnalyticsEvent.MealComposerOpened,
        AnalyticsEvent.MealPublished(MealSlot.Lunch, ingredientCount = 2, hasDescription = true, audienceCrewCount = 1, source = PublishSource.CAMERA),
        AnalyticsEvent.MealPublishFailed("PublishUnavailable"),
        AnalyticsEvent.MealDeleted(byAuthor = true),
        AnalyticsEvent.MealOpened(mealId),
        AnalyticsEvent.MealRated(mealId, score = 4),
        AnalyticsEvent.CommentPosted(mealId),
        AnalyticsEvent.MealReacted(mealId, reactionKind = "daily_glyph"),
        AnalyticsEvent.FeedDayViewed(mealCount = 5, dayOffset = 0),
        AnalyticsEvent.AchievementUnlocked("first_plate"),
        AnalyticsEvent.StreakViewed,
        AnalyticsEvent.LeaderboardViewed,
        AnalyticsEvent.DigestStoryOpened(DigestStorySource.NOTIFICATION, sceneCount = 5),
        AnalyticsEvent.DigestStorySceneViewed(sceneKind = "top_meal", sceneIndex = 1),
        AnalyticsEvent.DigestStoryCompleted(sceneCount = 5),
        AnalyticsEvent.NotifPermissionPrompted(promptCount = 1),
        AnalyticsEvent.NotifPermissionGranted,
        AnalyticsEvent.NotifPermissionDenied,
        AnalyticsEvent.ScreenViewed(ScreenName("meal_detail")),
        AnalyticsEvent.AccountDeleted,
        AnalyticsEvent.ConsentGranted(version = 1),
    )

    @Test
    fun every_event_name_is_ga4_legal() {
        allEvents.forEach { e ->
            assertTrue(AnalyticsConfig.EVENT_NAME_REGEX.matches(e.name), "illegal event name: '${e.name}'")
            assertTrue(e.name.length <= AnalyticsConfig.MAX_EVENT_NAME_LENGTH, "event name too long: '${e.name}'")
            assertTrue(
                AnalyticsConfig.RESERVED_EVENT_PREFIXES.none { e.name.startsWith(it) },
                "event name uses a reserved prefix: '${e.name}'",
            )
        }
    }

    @Test
    fun no_event_redefines_an_auto_collected_name() {
        allEvents.forEach { e ->
            assertTrue(e.name !in AnalyticsConfig.AUTO_COLLECTED_NAMES, "redefines auto-collected event: '${e.name}'")
        }
    }

    @Test
    fun every_param_is_ga4_legal_and_within_limits() {
        allEvents.forEach { e ->
            assertTrue(
                e.params.size <= AnalyticsConfig.MAX_PARAMS_PER_EVENT,
                "${e.name} has too many params: ${e.params.size}",
            )
            e.params.forEach { (key, value) ->
                assertTrue(AnalyticsConfig.EVENT_NAME_REGEX.matches(key), "illegal param name '$key' on '${e.name}'")
                assertTrue(key.length <= AnalyticsConfig.MAX_PARAM_NAME_LENGTH, "param name too long: '$key'")
                if (value is AnalyticsValue.Text) {
                    assertTrue(
                        value.value.length <= AnalyticsConfig.MAX_PARAM_VALUE_LENGTH,
                        "param '$key' value too long on '${e.name}'",
                    )
                }
            }
        }
    }

    @Test
    fun no_param_name_is_pii() {
        // Exact-key denylist (token match) + substring for unambiguous PII fragments. 'screen_name'
        // is NOT PII (a screen identifier), so we match keys exactly rather than by substring "name".
        val forbiddenKeys = setOf(
            "email", "name", "display_name", "full_name", "handle", "password", "phone",
            "phone_number", "address", "latitude", "longitude", "lat", "lng", "lon",
            "comment_text", "description", "crew_name", "dish_name", "message",
        )
        val forbiddenFragments = listOf("email", "password")
        allEvents.forEach { e ->
            e.params.keys.forEach { key ->
                assertTrue(key !in forbiddenKeys, "param '$key' on '${e.name}' is PII")
                assertTrue(forbiddenFragments.none { key.contains(it) }, "param '$key' on '${e.name}' is PII")
            }
        }
    }

    @Test
    fun share_card_events_reuse_the_share_name_with_content_type_and_item_id() {
        // Locks the "reuse `share`, never invent a new event" decision (spec §11): each share-card
        // leaf emits name == "share", a content_type in {plate, award, streak}, and an item_id; and
        // no param carries a display name (the no-PII guard already in [no_param_name_is_pii]).
        val shareCards = listOf(
            AnalyticsEvent.PlateShared(mealId) to "plate",
            AnalyticsEvent.AwardShared(mealId) to "award",
            AnalyticsEvent.StreakShared(streakDays = 14) to "streak",
            AnalyticsEvent.RecapShared(sceneKind = "your_week") to "recap",
        )
        shareCards.forEach { (event, expectedContentType) ->
            assertTrue(event.name == "share", "share-card event must reuse `share`: '${event.name}'")
            val contentType = (event.params["content_type"] as? AnalyticsValue.Text)?.value
            assertTrue(
                contentType == expectedContentType,
                "expected content_type=$expectedContentType, got '$contentType'",
            )
            assertTrue(contentType in setOf("plate", "award", "streak", "recap"), "illegal content_type '$contentType'")
            assertTrue(event.params.containsKey("item_id"), "share-card event missing item_id: '${event.name}'")
            assertTrue(
                event.params["item_id"] is AnalyticsValue.Text,
                "share-card event item_id must be Text (GA4 string), got ${event.params["item_id"]?.let { it::class.simpleName }} on '${event.name}'",
            )
        }
    }

    @Test
    fun user_property_keys_are_within_limits() {
        UserProperty.entries.forEach { p ->
            assertTrue(AnalyticsConfig.EVENT_NAME_REGEX.matches(p.key), "illegal user-property key: '${p.key}'")
            assertTrue(
                p.key.length <= AnalyticsConfig.MAX_USER_PROPERTY_NAME_LENGTH,
                "user-property key too long: '${p.key}'",
            )
        }
    }
}
