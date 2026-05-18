package es.schsebastian.foodrats.core.domain.meal

enum class MealSlot {
    Breakfast, Lunch, Dinner;

    fun key(): String = name.lowercase()

    companion object {
        fun fromKey(key: String): MealSlot? = entries.firstOrNull { it.key() == key }

        /**
         * Default slot to pre-select in the picker, based on the local hour-of-day.
         * 0..10 → Breakfast, 11..15 → Lunch, 16..23 → Dinner.
         */
        fun defaultForHour(hour: Int): MealSlot = when {
            hour < 11 -> Breakfast
            hour < 16 -> Lunch
            else -> Dinner
        }
    }
}
