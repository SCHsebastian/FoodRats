package es.schsebastian.foodrats.feature.notifications.domain.bus

import app.cash.turbine.test
import es.schsebastian.foodrats.feature.notifications.domain.model.Reminder
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderKind
import es.schsebastian.foodrats.feature.notifications.domain.model.ReminderPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class NotificationBusTest {

    private fun reminder(id: String) = Reminder(
        id = id,
        kind = ReminderKind.NewMealPost,
        deliverAt = Instant.fromEpochSeconds(0),
        title = "t-$id",
        body = "b-$id",
        payload = ReminderPayload.None,
    )

    @Test
    fun reminder_published_with_no_collector_is_buffered_not_dropped() = runTest {
        // The in-app banner collects only while RESUMED; a push landing while the UI is paused
        // (or before first composition) must survive until collection starts. The old
        // SharedFlow(replay = 0) bus dropped it silently — this locks the Channel semantics.
        val bus = NotificationBus()
        bus.publish(reminder("early"))

        bus.stream.test {
            assertEquals("early", awaitItem().id)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun each_reminder_is_delivered_exactly_once_across_resubscription() = runTest {
        // repeatOnLifecycle cancels and restarts collection; a resubscribing collector must see
        // only reminders it hasn't consumed yet — no replay of already-shown banners.
        val bus = NotificationBus()
        bus.publish(reminder("first"))

        bus.stream.test {
            assertEquals("first", awaitItem().id)
            cancelAndIgnoreRemainingEvents()
        }

        bus.publish(reminder("second"))
        bus.stream.test {
            assertEquals("second", awaitItem().id)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun overflow_drops_the_oldest_reminder() = runTest {
        // Capacity is 8; the 9th buffered publish evicts the stalest banner, never blocks the
        // OS-side receiver, and keeps the most recent pushes.
        val bus = NotificationBus()
        repeat(9) { bus.publish(reminder("r$it")) }

        bus.stream.test {
            // r0 was evicted; r1..r8 remain in order.
            for (i in 1..8) assertEquals("r$i", awaitItem().id)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
