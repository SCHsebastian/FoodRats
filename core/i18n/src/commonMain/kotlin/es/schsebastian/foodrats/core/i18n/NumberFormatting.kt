package es.schsebastian.foodrats.core.i18n

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats this value with exactly [decimals] fraction digits, ALWAYS using `.` as the decimal
 * separator (locale-independent) and half-up rounding.
 *
 * Why this exists — the `%f` trap behind [resolve]: Compose Resources' `stringResource(id, *args)`
 * (the engine under [resolve]) does NOT delegate to platform `String.format`. On every target it
 * runs its own substitution (`replaceWithArgs`, CMP 1.11.0, regex `%(\d+)\$[ds]`) that matches
 * ONLY `%n$d` and `%n$s` placeholders. A `%n$.Nf` float placeholder never matches: it is emitted
 * VERBATIM (you see a literal `%1$.1f` on screen) and its argument is silently dropped — which is
 * exactly how a vote/score average renders "with %" instead of the number.
 *
 * The fix is to pre-format every decimal here and pass it to a `%n$s` placeholder. Never put a
 * `%f` in a `strings.xml`. (Integers are fine on `%n$d` — they round-trip through `toString()`.)
 *
 * Examples: `4.5.toFixed(1) == "4.5"`, `5.0.toFixed(1) == "5.0"`,
 * `(-73.985428).toFixed(5) == "-73.98543"`.
 */
fun Double.toFixed(decimals: Int): String {
    require(isFinite()) { "toFixed requires a finite value, was $this" }
    require(decimals >= 0) { "decimals must be >= 0, was $decimals" }
    val negative = this < 0.0
    val magnitude = abs(this)
    if (decimals == 0) {
        val rounded = magnitude.roundToLong()
        return if (negative && rounded != 0L) "-$rounded" else rounded.toString()
    }
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = (magnitude * factor).roundToLong()
    val whole = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    val sign = if (negative && scaled != 0L) "-" else ""
    return "$sign$whole.$frac"
}
