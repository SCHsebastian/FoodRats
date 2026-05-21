package es.schsebastian.foodrats.feature.stats.domain.compute

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RangeHelpersTest {

    @Test fun startOfIsoWeek_returns_monday_for_any_day() {
        // Wednesday 2026-05-20 → Monday 2026-05-18
        assertEquals(LocalDate(2026, 5, 18), startOfIsoWeek(LocalDate(2026, 5, 20)))
        // Sunday 2026-05-24 → Monday 2026-05-18
        assertEquals(LocalDate(2026, 5, 18), startOfIsoWeek(LocalDate(2026, 5, 24)))
        // Monday itself → itself
        assertEquals(LocalDate(2026, 5, 18), startOfIsoWeek(LocalDate(2026, 5, 18)))
    }

    @Test fun startOfMonth_returns_day_one() {
        assertEquals(LocalDate(2026, 5, 1), startOfMonth(LocalDate(2026, 5, 20)))
        assertEquals(LocalDate(2026, 2, 1), startOfMonth(LocalDate(2026, 2, 28)))
    }

    @Test fun currentRangeFor_covers_min_of_iso_week_and_month_start() {
        // Today 2026-05-03 (Sunday). ISO week starts 2026-04-27 (Mon); month starts 2026-05-01.
        // min → 2026-04-27.
        assertEquals(LocalDate(2026, 4, 27) to LocalDate(2026, 5, 3), currentRangeFor(LocalDate(2026, 5, 3)))
        // Today 2026-05-15 (Friday). ISO week 2026-05-11 ; month 2026-05-01. min → 2026-05-01.
        assertEquals(LocalDate(2026, 5, 1) to LocalDate(2026, 5, 15), currentRangeFor(LocalDate(2026, 5, 15)))
    }

    @Test fun daysInclusive_counts_inclusive_range() {
        assertEquals(7, daysInclusive(LocalDate(2026, 5, 1), LocalDate(2026, 5, 7)))
        assertEquals(1, daysInclusive(LocalDate(2026, 5, 1), LocalDate(2026, 5, 1)))
    }
}
