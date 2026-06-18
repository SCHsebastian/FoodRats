package es.schsebastian.foodrats.feature.stats.presentation.components

import kotlin.math.round

/**
 * Formats a score to a single decimal place (e.g. `4.7`), the shared one-decimal renderer for every
 * stats card. Negative tenths are normalized so a value like `-0.2` reads `0.2`, matching the
 * podium/roast/cook/window cards that all consume non-negative scores.
 */
internal fun formatOneDecimal(v: Float): String {
    val rounded = round(v * 10f) / 10f
    val whole = rounded.toInt()
    val tenths = ((rounded - whole) * 10f).toInt()
    return "$whole.${if (tenths < 0) -tenths else tenths}"
}
