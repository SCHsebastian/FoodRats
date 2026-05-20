package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimestampTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    @Test fun under_60s_is_just_now() {
        val rel = (now - 30.seconds).toRelative(now)
        assertEquals(FeedStringKey.CommentsRelativeJustNow, rel.key)
        assertEquals(0, rel.amount)
    }

    @Test fun under_1h_is_minutes() {
        val rel = (now - 5.minutes).toRelative(now)
        assertEquals(FeedStringKey.CommentsRelativeMinutes, rel.key)
        assertEquals(5, rel.amount)
    }

    @Test fun under_24h_is_hours() {
        val rel = (now - 3.hours - 30.minutes).toRelative(now)
        assertEquals(FeedStringKey.CommentsRelativeHours, rel.key)
        assertEquals(3, rel.amount)
    }

    @Test fun at_least_1_day_is_days() {
        val rel = (now - 2.days).toRelative(now)
        assertEquals(FeedStringKey.CommentsRelativeDays, rel.key)
        assertEquals(2, rel.amount)
    }

    @Test fun future_clamps_to_just_now() {
        val rel = (now + 5.seconds).toRelative(now)
        assertEquals(FeedStringKey.CommentsRelativeJustNow, rel.key)
    }
}
