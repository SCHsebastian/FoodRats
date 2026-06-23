package es.schsebastian.foodrats.feature.stats.presentation.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Compact `dd/MM` in the device zone, shared by the passport and pokédex cells. Numeric (no CLDR month
 * names, which aren't uniformly available in commonMain) so it renders identically on both platforms,
 * and the surrounding "Collected/Caught %1$s" wrapper is the localized part. Dropped the 4-digit year
 * (was ISO `yyyy-MM-dd`) so the caption fits one line in the narrow 4-up grid cell instead of
 * truncating ("Conseguid…").
 */
internal fun formatCollectionDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$day/$month"
}
