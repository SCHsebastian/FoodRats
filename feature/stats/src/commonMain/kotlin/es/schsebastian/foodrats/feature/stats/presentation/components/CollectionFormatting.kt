package es.schsebastian.foodrats.feature.stats.presentation.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * ISO `yyyy-MM-dd` in the device zone, shared by the passport and pokédex cells. Same rationale as
 * the achievements `formatEpochDay`: CLDR month names aren't uniformly available in commonMain, ISO
 * renders identically on both platforms, and the surrounding "Collected/Caught %1$s" wrapper is the
 * localized part.
 */
internal fun formatCollectionDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$month-$day"
}
