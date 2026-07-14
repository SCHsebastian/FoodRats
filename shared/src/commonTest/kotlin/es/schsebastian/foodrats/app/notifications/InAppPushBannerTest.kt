package es.schsebastian.foodrats.app.notifications

import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class InAppPushBannerTest {

    private val separator = " — "

    private fun reminder(title: String, body: String) = Reminder(
        id = "r1",
        kind = ReminderKind.NewMealPost,
        deliverAt = Instant.fromEpochMilliseconds(0),
        title = title,
        body = body,
        payload = ReminderPayload.None,
    )

    @Test
    fun joins_title_and_body_with_separator() {
        assertEquals(
            "Sam posted a meal — Tortilla — tap to view",
            reminderToSnackbarMessage(reminder("Sam posted a meal", "Tortilla — tap to view"), separator),
        )
    }

    @Test
    fun omits_blank_body() {
        assertEquals(
            "Your week in food",
            reminderToSnackbarMessage(reminder("Your week in food", ""), separator),
        )
    }

    @Test
    fun omits_blank_title() {
        assertEquals(
            "Tap to read",
            reminderToSnackbarMessage(reminder("", "Tap to read"), separator),
        )
    }

    @Test
    fun returns_blank_when_both_blank() {
        assertEquals("", reminderToSnackbarMessage(reminder("", ""), separator))
    }
}
