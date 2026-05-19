package es.schsebastian.foodrats.core.domain.meal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class MealDayTest {
    private val zone = TimeZone.UTC
    private fun day(s: String) = MealDay(LocalDate.parse(s), zone)

    @Test fun same_day_is_zero() {
        assertEquals(0, day("2026-05-19").daysSince(day("2026-05-19")))
    }

    @Test fun one_day_later_is_one() {
        assertEquals(1, day("2026-05-20").daysSince(day("2026-05-19")))
    }

    @Test fun earlier_is_negative() {
        assertEquals(-1, day("2026-05-18").daysSince(day("2026-05-19")))
    }
}
