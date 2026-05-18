package es.schsebastian.foodrats.core.domain.meal

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
        val hash = day.toKey().hashCode()
        val index = ((hash % POOL.size) + POOL.size) % POOL.size
        return POOL[index]
    }
}
