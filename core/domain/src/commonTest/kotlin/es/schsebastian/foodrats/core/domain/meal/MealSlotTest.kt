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

    @Test
    fun forHour_maps_late_night_and_early_morning_to_snack() {
        assertEquals(MealSlot.Snack, MealSlot.forHour(4))
        assertEquals(MealSlot.Snack, MealSlot.forHour(0))
    }

    @Test
    fun forHour_maps_breakfast_boundaries() {
        assertEquals(MealSlot.Breakfast, MealSlot.forHour(5))
        assertEquals(MealSlot.Breakfast, MealSlot.forHour(10))
    }

    @Test
    fun forHour_maps_brunch_boundaries() {
        assertEquals(MealSlot.Brunch, MealSlot.forHour(11))
        assertEquals(MealSlot.Brunch, MealSlot.forHour(12))
    }

    @Test
    fun forHour_maps_lunch_boundaries() {
        assertEquals(MealSlot.Lunch, MealSlot.forHour(13))
        assertEquals(MealSlot.Lunch, MealSlot.forHour(16))
    }

    @Test
    fun forHour_maps_merienda_boundaries() {
        assertEquals(MealSlot.Merienda, MealSlot.forHour(17))
        assertEquals(MealSlot.Merienda, MealSlot.forHour(19))
    }

    @Test
    fun forHour_maps_dinner_boundaries() {
        assertEquals(MealSlot.Dinner, MealSlot.forHour(20))
        assertEquals(MealSlot.Dinner, MealSlot.forHour(23))
    }
}
