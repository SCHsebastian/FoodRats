package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.runtime.Immutable
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.time.Instant

/**
 * Marked [Immutable]: [StringKey] is an interface so Compose infers it unstable, but every leaf is an
 * enum entry / data object — the promise is sound, and it keeps [CommentRowUi] (and [FeedMealUi]'s
 * consumers) skippable.
 */
@Immutable
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
