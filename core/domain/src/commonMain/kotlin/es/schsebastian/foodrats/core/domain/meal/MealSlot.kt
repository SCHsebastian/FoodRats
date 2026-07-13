package es.schsebastian.foodrats.core.domain.meal

/**
 * The optional "meal moment" a plate is tagged with. Purely a descriptive label now — it no
 * longer gates how many meals you may post in a day (that's a flat per-crew daily cap, see
 * [MealPublishPolicy]) and several plates may share a slot (or carry none).
 *
 * Ordered chronologically so the composer/feed render them in day order. The persisted key is the
 * lowercased name (`"breakfast"`, `"brunch"`, …); Spanish labels live in i18n (`brunch` keeps the
 * loanword, `lunch` stays "Almuerzo", `merienda` is the afternoon snack).
 */
enum class MealSlot {
    Breakfast, Brunch, Lunch, Snack, Merienda, Dinner;

    fun key(): String = name.lowercase()

    companion object {
        fun fromKey(key: String): MealSlot? = entries.firstOrNull { it.key() == key }

        /**
         * Suggests a [MealSlot] from a 24h [hour] (0–23), for EXIF-capture-time prefill on the
         * gallery photo-pick path. This is purely a heuristic default the user can freely override
         * or clear — it is NOT a constraint on what slot a meal may carry.
         *
         * Mapping (Spanish-leaning app): 5–10 Breakfast, 11–12 Brunch, 13–16 Lunch, 17–19
         * Merienda, 20–23 Dinner, 0–4 Snack (late night / early morning).
         */
        fun forHour(hour: Int): MealSlot = when (hour) {
            in 5..10 -> Breakfast
            in 11..12 -> Brunch
            in 13..16 -> Lunch
            in 17..19 -> Merienda
            in 20..23 -> Dinner
            else -> Snack
        }
    }
}
