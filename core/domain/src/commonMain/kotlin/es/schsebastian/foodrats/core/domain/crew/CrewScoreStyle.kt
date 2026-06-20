package es.schsebastian.foodrats.core.domain.crew

/**
 * The Crew's chosen Score vocabulary: how the [es.schsebastian.foodrats.core.domain.meal.Score]
 * value is displayed and picked by crew members. The underlying numeric Score (1..5) is always
 * stored as-is; only the rendering layer translates it.
 *
 * Lives in `:core:domain` (vendor-free) so the feed's presentation layer can map it to an
 * `FrScoreStyle` without touching `:feature:crew`.
 *
 * Leaves are [data object]s — not an enum — so future leaves (e.g. a custom label style)
 * can carry payloads. (House rule: sealed interfaces over enums for domain errors/styles.)
 *
 * Default is [Stars], matching pre-C8 behavior for all existing crews.
 */
sealed interface CrewScoreStyle {
    /** Classic 1–5 stars (★) — the default before C8. */
    data object Stars : CrewScoreStyle
    /** Emoji scale derived from the numeric score (😐 … 🤩). */
    data object Emoji : CrewScoreStyle
    /** Plain numeric label — "N/5". */
    data object Numeric : CrewScoreStyle
}
