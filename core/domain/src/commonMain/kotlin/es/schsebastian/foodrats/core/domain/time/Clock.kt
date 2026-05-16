package es.schsebastian.foodrats.core.domain.time

import kotlinx.datetime.Clock as KotlinClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

interface Clock {
    fun now(): Instant
}

class SystemClock : Clock {
    override fun now(): Instant = KotlinClock.System.now()
}

class FixedClock(private var current: Instant) : Clock {
    override fun now(): Instant = current
    fun advanceBy(unit: DateTimeUnit, value: Long) {
        current = current.plus(value, unit, TimeZone.UTC)
    }
    fun set(instant: Instant) { current = instant }
}
