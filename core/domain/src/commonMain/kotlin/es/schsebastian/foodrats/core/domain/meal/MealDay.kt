package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

data class MealDay(val date: LocalDate, val zone: TimeZone) {
    fun toKey(): String = date.toString()

    fun daysSince(other: MealDay): Int = other.date.daysUntil(this.date)

    companion object {
        fun today(clock: Clock, zone: TimeZone): MealDay =
            MealDay(clock.now().toLocalDateTime(zone).date, zone)
    }
}
