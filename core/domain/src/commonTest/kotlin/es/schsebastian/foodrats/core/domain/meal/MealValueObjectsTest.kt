package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.time.FixedClock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealValueObjectsTest {
    @Test fun score_accepts_1_through_10() {
        for (v in 1..10) assertTrue(Score.of(v) is Result.Ok)
    }
    @Test fun score_rejects_0_and_11() {
        assertTrue(Score.of(0) is Result.Err)
        assertTrue(Score.of(11) is Result.Err)
        assertTrue(Score.of(-3) is Result.Err)
    }
    @Test fun dishName_rejects_blank() {
        assertTrue(DishName.of("   ") is Result.Err)
        assertTrue(DishName.of("") is Result.Err)
    }
    @Test fun dishName_rejects_too_long() {
        val r = DishName.of("x".repeat(61))
        assertTrue(r is Result.Err)
        assertEquals(MealValueObjectError.DishNameTooLong, (r as Result.Err).error)
    }
    @Test fun dishName_trims() {
        val r = DishName.of("  Pizza  ")
        assertTrue(r is Result.Ok)
        assertEquals("Pizza", (r as Result.Ok).value.value)
    }
    @Test fun mealDay_today_uses_clock_and_zone() {
        val clock = FixedClock(Instant.parse("2026-05-16T12:00:00Z"))
        val day = MealDay.today(clock, TimeZone.of("Europe/Madrid"))
        assertEquals(LocalDate(2026, 5, 16), day.date)
    }
    @Test fun mealDay_toKey_is_ISO_date() {
        val day = MealDay(LocalDate(2026, 1, 9), TimeZone.UTC)
        assertEquals("2026-01-09", day.toKey())
    }
    @Test fun mealId_rejects_blank() {
        assertTrue(MealId.of(" ") is Result.Err)
    }

    @Test
    fun MealId_forDaySlot_builds_deterministic_id() {
        val crew = CrewId("crew-42")
        val author = AccountId("u-7")
        val day = MealDay(LocalDate(2026, 5, 18), TimeZone.UTC)

        assertEquals(
            MealId("crew-42_u-7_2026-05-18_breakfast"),
            MealId.forDaySlot(crew, author, day, MealSlot.Breakfast),
        )
    }

    @Test
    fun MealId_forDaySlot_is_stable_across_calls() {
        val crew = CrewId("crew-42")
        val author = AccountId("u-7")
        val day = MealDay(LocalDate(2026, 5, 18), TimeZone.UTC)
        assertEquals(
            MealId.forDaySlot(crew, author, day, MealSlot.Dinner),
            MealId.forDaySlot(crew, author, day, MealSlot.Dinner),
        )
    }
}
