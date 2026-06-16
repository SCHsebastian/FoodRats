package es.schsebastian.foodrats.feature.achievements.presentation.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Formats an unlock epoch-millisecond into a stable, locale-neutral ISO date (`yyyy-MM-dd`) in the
 * device time zone. ISO is chosen deliberately: cross-platform CLDR month-name formatting is not
 * available uniformly in commonMain, and an ISO date renders identically on Android + iOS. The
 * surrounding "Earned %1$s" wrapper is localized via `AchievementStringKey.EarnedOnFormat`.
 */
internal fun formatEpochDay(epochMs: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}
