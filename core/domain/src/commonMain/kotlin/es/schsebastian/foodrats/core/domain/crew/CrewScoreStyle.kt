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
    /**
     * Stable, persisted discriminator — matches the Firestore DTO value
     * (`CrewMapper.toDto`) AND the value the write outbox flattens
     * ([es.schsebastian.foodrats.core.domain.outbox.PendingCommand.SetCrewScoreStyle.styleKey]).
     * Lowercase, never localized.
     */
    val key: String

    /** Classic 1–5 stars (★) — the default before C8. */
    data object Stars : CrewScoreStyle {
        override val key: String = "stars"
    }
    /** Emoji scale derived from the numeric score (😐 … 🤩). */
    data object Emoji : CrewScoreStyle {
        override val key: String = "emoji"
    }
    /** Plain numeric label — "N/5". */
    data object Numeric : CrewScoreStyle {
        override val key: String = "numeric"
    }

    companion object {
        /** Every style, for exhaustive mapping and tests. */
        val all: List<CrewScoreStyle> = listOf(Stars, Emoji, Numeric)

        /** Resolves a persisted [key] back to its style, or `null` if unknown (forward-compat). */
        fun fromKey(key: String): CrewScoreStyle? = all.firstOrNull { it.key == key }
    }
}
