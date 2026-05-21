package es.schsebastian.foodrats.feature.stats.domain.compute

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

internal fun startOfIsoWeek(date: LocalDate): LocalDate {
    val offset = when (date.dayOfWeek) {
        DayOfWeek.MONDAY    -> 0
        DayOfWeek.TUESDAY   -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY  -> 3
        DayOfWeek.FRIDAY    -> 4
        DayOfWeek.SATURDAY  -> 5
        DayOfWeek.SUNDAY    -> 6
    }
    return date.minus(DatePeriod(days = offset))
}

internal fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

internal fun currentRangeFor(today: LocalDate): Pair<LocalDate, LocalDate> {
    val a = startOfIsoWeek(today)
    val b = startOfMonth(today)
    val from = if (a < b) a else b
    return from to today
}

internal fun daysInclusive(from: LocalDate, to: LocalDate): Int = from.daysUntil(to) + 1
