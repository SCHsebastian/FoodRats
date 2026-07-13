package es.schsebastian.foodrats.core.domain.meal

import kotlin.test.Test
import kotlin.test.assertEquals

class MealPublishPolicyTest {

    @Test
    fun max_photos_per_meal_is_10() {
        assertEquals(10, MealPublishPolicy.MAX_PHOTOS_PER_MEAL)
    }

    @Test
    fun max_meals_per_crew_per_day_is_10() {
        assertEquals(10, MealPublishPolicy.MAX_MEALS_PER_CREW_PER_DAY)
    }
}
