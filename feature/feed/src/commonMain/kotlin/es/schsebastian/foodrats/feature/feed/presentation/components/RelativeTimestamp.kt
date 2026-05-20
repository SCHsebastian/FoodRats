package es.schsebastian.foodrats.feature.feed.presentation.components

import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.time.Instant

data class RelativeTimestamp(val key: StringKey, val amount: Int)

fun Instant.toRelative(now: Instant): RelativeTimestamp {
    val secs = (now.toEpochMilliseconds() - this.toEpochMilliseconds()) / 1000L
    return when {
        secs < 60       -> RelativeTimestamp(FeedStringKey.CommentsRelativeJustNow, 0)
        secs < 3_600    -> RelativeTimestamp(FeedStringKey.CommentsRelativeMinutes, (secs / 60).toInt())
        secs < 86_400   -> RelativeTimestamp(FeedStringKey.CommentsRelativeHours, (secs / 3_600).toInt())
        else            -> RelativeTimestamp(FeedStringKey.CommentsRelativeDays, (secs / 86_400).toInt())
    }
}
