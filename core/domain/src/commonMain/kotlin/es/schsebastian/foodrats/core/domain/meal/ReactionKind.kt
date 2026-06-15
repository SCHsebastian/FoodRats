package es.schsebastian.foodrats.core.domain.meal

/**
 * The fixed set of reaction glyphs a crew member can leave on a meal.
 *
 * A reaction is the lightweight, expressive counterpart to a numeric [Score]: a single
 * tasteful affirmation, NOT a like-counter and NOT a vote. The set is deliberately small
 * and FIXED — never free text — so the feed stays tasteful and the data stays structured.
 *
 * ## Parked default (roadmap §1.3): the daily glyph only
 * The MVP ships exactly one kind, [DailyGlyph]: the reaction's rendered glyph is the
 * meal-day's deterministic [DailyEmote] (`DailyEmote.forDay(meal.day)`), shared across all
 * crew members for that day. Reinforcing the daily ritual was preferred over a static
 * 😋🔥🤤 picker. The glyph is therefore DERIVED from the meal's day at render time and is
 * NOT persisted on the reaction — only the *fact that the member reacted* is stored.
 *
 * Modeled as a `sealed interface` with `data object` leaves (not an enum) so a future small
 * fixed set (e.g. distinct `Yum` / `Fire` / `Drool` leaves, each persisting its own
 * discriminator) can be added without breaking the [MealReactionPort] contract or the
 * stored shape. Each leaf carries a stable [key] for the data layer to persist.
 */
sealed interface ReactionKind {
    /** Stable, persisted discriminator. Snake_case, never localized, never the glyph itself. */
    val key: String

    /**
     * The day-themed reaction: its glyph is `DailyEmote.forDay(meal.day)`, resolved at
     * render time. The only kind shipped by the MVP.
     */
    data object DailyGlyph : ReactionKind {
        override val key: String = "daily_glyph"
    }

    companion object {
        /** Every kind, for exhaustive data-layer mapping and tests. */
        val all: List<ReactionKind> = listOf(DailyGlyph)

        /** Resolves a persisted [key] back to its kind, or `null` if unknown (forward-compat). */
        fun fromKey(key: String): ReactionKind? = all.firstOrNull { it.key == key }
    }
}
