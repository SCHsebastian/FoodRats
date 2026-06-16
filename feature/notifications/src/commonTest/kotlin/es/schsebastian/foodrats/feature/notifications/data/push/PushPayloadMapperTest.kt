package es.schsebastian.foodrats.feature.notifications.data.push

import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

private class FixedTestClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * Tests the pure [PushPayloadMapper.parse] step. Display text is built in [PushPayloadMapper.toReminder]
 * via the suspending Compose-resources `getString(...)`, which throws under `commonTest` (resources are
 * not bundled), so title/body formatting is intentionally NOT exercised here — only the locale-free
 * parsing of kind, id, structured fields, and payload.
 */
class PushPayloadMapperTest {

    private val fixedNow = Instant.fromEpochMilliseconds(1_716_000_000_000)
    private val mapper = PushPayloadMapper(FixedTestClock(fixedNow))

    @Test
    fun new_comment_payload_parses_to_NewComment_content() {
        val data = mapOf(
            "kind" to "NewComment",
            "key" to "new_comment",
            "crewId" to "C1",
            "mealId" to "M1",
            "commentId" to "X1",
            "commenterName" to "Alex",
            "dishName" to "Tortilla",
        )
        val content = assertIs<PushPayloadMapper.PushContent.NewComment>(mapper.parse(data))
        assertEquals(ReminderKind.NewComment, content.kind)
        assertEquals("X1", content.id)
        assertEquals("Alex", content.commenterName)
        assertEquals("Tortilla", content.dishName)
        val payload = assertIs<ReminderPayload.Comment>(content.payload)
        assertEquals("C1", payload.crewId)
        assertEquals("M1", payload.mealId)
        assertEquals("X1", payload.commentId)
    }

    @Test
    fun new_meal_post_payload_parses_to_NewMealPost_content() {
        val data = mapOf(
            "kind" to "NewMealPost",
            "key" to "new_meal_post",
            "crewId" to "C1",
            "mealId" to "M2",
            "authorName" to "Sam",
            "dishName" to "Lentejas",
        )
        val content = assertIs<PushPayloadMapper.PushContent.NewMealPost>(mapper.parse(data))
        assertEquals(ReminderKind.NewMealPost, content.kind)
        assertEquals("M2", content.id)
        assertEquals("Sam", content.authorName)
        assertEquals("Lentejas", content.dishName)
        val payload = assertIs<ReminderPayload.Meal>(content.payload)
        assertEquals("C1", payload.crewId)
        assertEquals("M2", payload.mealId)
    }

    @Test
    fun weekly_digest_payload_parses_to_WeeklyDigest_content() {
        val data = mapOf(
            "kind" to "WeeklyDigest",
            "key" to "weekly_digest",
            "crewId" to "C1",
            "weekStartIso" to "2026-05-12",
            "bestMealDishName" to "Tortilla",
            "bestMealScore" to "4.70",
            "bestCookName" to "Alex",
            "mostProlificName" to "Sam",
            "mostProlificCount" to "9",
        )
        val content = assertIs<PushPayloadMapper.PushContent.WeeklyDigest>(mapper.parse(data))
        assertEquals(ReminderKind.WeeklyDigest, content.kind)
        assertEquals("weekly-2026-05-12", content.id)
        val payload = assertIs<ReminderPayload.WeeklyDigest>(content.payload)
        assertEquals("C1", payload.crewId)
        assertEquals("2026-05-12", payload.weekStartIso)
    }

    @Test
    fun social_nudge_payload_parses_to_SocialNudge_content() {
        val data = mapOf(
            "kind" to "SocialNudge",
            "key" to "social_nudge",
            "postedCount" to "3",
            "crewSize" to "5",
        )
        val content = assertIs<PushPayloadMapper.PushContent.SocialNudge>(mapper.parse(data))
        assertEquals(ReminderKind.SocialNudge, content.kind)
        assertEquals("social-nudge", content.id)
        assertEquals(3, content.postedCount)
        assertEquals(5, content.crewSize)
        // No deep target — a tap just opens the app to Feed.
        assertEquals(ReminderPayload.None, content.payload)
    }

    @Test
    fun social_nudge_with_non_numeric_count_returns_null() {
        val data = mapOf("key" to "social_nudge", "postedCount" to "lots", "crewSize" to "5")
        assertNull(mapper.parse(data))
    }

    @Test
    fun social_nudge_missing_crew_size_returns_null() {
        val data = mapOf("key" to "social_nudge", "postedCount" to "3")
        assertNull(mapper.parse(data))
    }

    @Test
    fun missing_required_field_returns_null() {
        // key present but mealId missing → unparseable
        val data = mapOf("key" to "new_meal_post", "crewId" to "C1")
        assertNull(mapper.parse(data))
    }

    @Test
    fun missing_key_returns_null() {
        val data = mapOf("crewId" to "C1")
        assertNull(mapper.parse(data))
    }

    @Test
    fun unknown_key_returns_null() {
        val data = mapOf("key" to "totally_made_up")
        assertNull(mapper.parse(data))
    }
}
