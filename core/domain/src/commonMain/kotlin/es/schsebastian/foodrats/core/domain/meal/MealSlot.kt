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
    }
}
