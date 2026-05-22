package es.schsebastian.foodrats.feature.notifications.data.push

import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FixedTestClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class PushPayloadMapperTest {

    private val fixedNow = Instant.fromEpochMilliseconds(1_716_000_000_000)
    private val mapper = PushPayloadMapper(FixedTestClock(fixedNow))

    @Test
    fun new_comment_payload_maps_to_NewComment_reminder() {
        val data = mapOf(
            "kind" to "NewComment",
            "key" to "new_comment",
            "crewId" to "C1",
            "mealId" to "M1",
            "commentId" to "X1",
            "commenterName" to "Alex",
            "dishName" to "Tortilla",
        )
        val reminder = mapper.toReminder(data)!!
        assertEquals(ReminderKind.NewComment, reminder.kind)
        assertEquals("X1", reminder.id)
        assertEquals(fixedNow, reminder.deliverAt)
        assertTrue(reminder.title.contains("Alex"))
        assertTrue(reminder.title.contains("Tortilla"))
        val payload = reminder.payload as ReminderPayload.Comment
        assertEquals("C1", payload.crewId)
        assertEquals("M1", payload.mealId)
        assertEquals("X1", payload.commentId)
    }

    @Test
    fun new_meal_post_payload_maps_to_NewMealPost_reminder() {
        val data = mapOf(
            "kind" to "NewMealPost",
            "key" to "new_meal_post",
            "crewId" to "C1",
            "mealId" to "M2",
            "authorName" to "Sam",
            "dishName" to "Lentejas",
        )
        val reminder = mapper.toReminder(data)!!
        assertEquals(ReminderKind.NewMealPost, reminder.kind)
        assertEquals("M2", reminder.id)
        assertTrue(reminder.title.contains("Sam"))
        val payload = reminder.payload as ReminderPayload.Meal
        assertEquals("C1", payload.crewId)
        assertEquals("M2", payload.mealId)
    }

    @Test
    fun weekly_digest_payload_maps_to_WeeklyDigest_reminder() {
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
        val reminder = mapper.toReminder(data)!!
        assertEquals(ReminderKind.WeeklyDigest, reminder.kind)
        val body = reminder.body
        assertTrue(body.contains("Tortilla"), "body should mention best meal: $body")
        assertTrue(body.contains("Alex"), "body should mention best cook: $body")
        assertTrue(body.contains("Sam"), "body should mention most prolific: $body")
        val payload = reminder.payload as ReminderPayload.WeeklyDigest
        assertEquals("C1", payload.crewId)
        assertEquals("2026-05-12", payload.weekStartIso)
    }

    @Test
    fun missing_key_returns_null() {
        val data = mapOf("crewId" to "C1")
        assertNull(mapper.toReminder(data))
    }

    @Test
    fun unknown_key_returns_null() {
        val data = mapOf("key" to "totally_made_up")
        assertNull(mapper.toReminder(data))
    }
}
