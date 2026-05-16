package es.schsebastian.foodrats.feature.feed.domain.model

import es.schsebastian.foodrats.core.domain.meal.MealDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Day-window cursor for the Feed. Wraps [MealDay] and exposes bounded prev/next
 * helpers. The MVP window is the last 30 days inclusive.
 */
data class FeedDay(val day: MealDay) {

    fun previous(): FeedDay = FeedDay(MealDay(day.date.minus(DatePeriod(days = 1)), day.zone))
    fun next(): FeedDay = FeedDay(MealDay(day.date.plus(DatePeriod(days = 1)), day.zone))

    companion object {
        const val WINDOW_DAYS = 30

        fun today(today: LocalDate, zone: TimeZone): FeedDay = FeedDay(MealDay(today, zone))

        fun isWithinWindow(candidate: LocalDate, today: LocalDate): Boolean {
            if (candidate > today) return false
            val cutoff = today.minus(DatePeriod(days = WINDOW_DAYS - 1))
            return candidate >= cutoff
        }
    }
}
