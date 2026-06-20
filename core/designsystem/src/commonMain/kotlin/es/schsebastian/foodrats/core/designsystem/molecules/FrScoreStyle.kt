package es.schsebastian.foodrats.core.designsystem.molecules

/**
 * Presentation enum for the Crew's chosen Score display vocabulary (C8).
 * Domain-free: the design system never imports `:core:domain`; callers map
 * `CrewScoreStyle` → `FrScoreStyle` in the presentation layer.
 *
 * Used by [FrStarRatingPicker] and the feed meal-card rating summary to switch rendering.
 */
enum class FrScoreStyle {
    /** Classic star glyphs (★). Default for pre-C8 crews. */
    Stars,
    /** Emoji scale derived from the numeric score: 😐 😊 😋 🤩 for 1–2, 3, 4, 5. */
    Emoji,
    /** Plain numeric label — "N/5". */
    Numeric,
}

/**
 * Maps a numeric Score (1..5) to a tasteful emoji for [FrScoreStyle.Emoji] display.
 * Never called for other styles.
 */
fun scoreToEmoji(score: Int): String = when (score.coerceIn(1, 5)) {
    1    -> "😐"
    2    -> "🙂"
    3    -> "😋"
    4    -> "😍"
    else -> "🤩"
}
