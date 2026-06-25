package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MealSlotTest {

    @Test
    fun keys_are_stable_lowercase_strings() {
        assertEquals("breakfast", MealSlot.Breakfast.key())
        assertEquals("brunch", MealSlot.Brunch.key())
        assertEquals("lunch", MealSlot.Lunch.key())
        assertEquals("snack", MealSlot.Snack.key())
        assertEquals("merienda", MealSlot.Merienda.key())
        assertEquals("dinner", MealSlot.Dinner.key())
    }

    @Test
    fun there_are_six_slots_in_chronological_order() {
        assertEquals(
            listOf(
                MealSlot.Breakfast, MealSlot.Brunch, MealSlot.Lunch,
                MealSlot.Snack, MealSlot.Merienda, MealSlot.Dinner,
            ),
            MealSlot.entries.toList(),
        )
    }

    @Test
    fun fromKey_round_trips_known_keys() {
        MealSlot.entries.forEach { slot ->
            assertEquals(slot, MealSlot.fromKey(slot.key()))
        }
    }

    @Test
    fun fromKey_returns_null_for_unknown() {
        assertEquals(null, MealSlot.fromKey("supper"))
        assertEquals(null, MealSlot.fromKey(""))
    }
}
