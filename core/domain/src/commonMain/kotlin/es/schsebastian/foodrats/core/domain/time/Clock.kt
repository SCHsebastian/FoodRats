package es.schsebastian.foodrats.core.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Clock as KotlinTimeClock
import kotlin.time.Instant

interface Clock {
    fun now(): Instant
}

class SystemClock : Clock {
    override fun now(): Instant = KotlinTimeClock.System.now()
}

class FixedClock(private var current: Instant) : Clock {
    override fun now(): Instant = current
    fun advanceBy(unit: DateTimeUnit, value: Long) {
        current = current.plus(value, unit, TimeZone.UTC)
    }
    fun set(instant: Instant) { current = instant }
}
