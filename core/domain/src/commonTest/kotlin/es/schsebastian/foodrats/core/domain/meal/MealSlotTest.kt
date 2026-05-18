package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MealSlotTest {

    @Test
    fun keys_are_stable_lowercase_strings() {
        assertEquals("breakfast", MealSlot.Breakfast.key())
        assertEquals("lunch", MealSlot.Lunch.key())
        assertEquals("dinner", MealSlot.Dinner.key())
    }

    @Test
    fun fromKey_round_trips_known_keys() {
        MealSlot.entries.forEach { slot ->
            assertEquals(slot, MealSlot.fromKey(slot.key()))
        }
    }

    @Test
    fun fromKey_returns_null_for_unknown() {
        assertEquals(null, MealSlot.fromKey("brunch"))
    }

    @Test
    fun defaultForHour_picks_breakfast_before_11() {
        assertEquals(MealSlot.Breakfast, MealSlot.defaultForHour(6))
        assertEquals(MealSlot.Breakfast, MealSlot.defaultForHour(10))
    }

    @Test
    fun defaultForHour_picks_lunch_11_to_16() {
        assertEquals(MealSlot.Lunch, MealSlot.defaultForHour(11))
        assertEquals(MealSlot.Lunch, MealSlot.defaultForHour(15))
    }

    @Test
    fun defaultForHour_picks_dinner_from_16() {
        assertEquals(MealSlot.Dinner, MealSlot.defaultForHour(16))
        assertEquals(MealSlot.Dinner, MealSlot.defaultForHour(23))
    }
}
