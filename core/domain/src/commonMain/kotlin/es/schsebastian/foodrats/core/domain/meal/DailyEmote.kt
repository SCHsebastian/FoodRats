package es.schsebastian.foodrats.core.domain.meal

import kotlin.math.absoluteValue

object DailyEmote {

    private val POOL = listOf(
        "🍕", "🍔", "🍣", "🥗", "🍜", "🌮", "🥐", "🍰",
        "🍩", "🥞", "🍝", "🥩", "🍱", "🌯", "🥘", "🍛",
        "🥪", "🍤", "🍙", "🥟",
    )

    /**
     * Returns the day's theme emote — deterministic for a given [day], identical
     * across all crew members and devices. Uses [MealDay.toKey] (ISO date) as the
     * seed; recompute on every read, never persist.
     */
    fun forDay(day: MealDay): String {
        val hash = day.toKey().hashCode().absoluteValue
        return POOL[hash % POOL.size]
    }
}
