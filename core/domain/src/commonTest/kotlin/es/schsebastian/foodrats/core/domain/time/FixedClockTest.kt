package es.schsebastian.foodrats.core.domain.time

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FixedClockTest {
    @Test fun returns_configured_instant() {
        val fixed = FixedClock(Instant.parse("2026-05-16T10:00:00Z"))
        assertEquals(Instant.parse("2026-05-16T10:00:00Z"), fixed.now())
    }

    @Test fun can_advance() {
        val fixed = FixedClock(Instant.parse("2026-05-16T10:00:00Z"))
        fixed.advanceBy(kotlinx.datetime.DateTimeUnit.SECOND, 30)
        assertEquals(Instant.parse("2026-05-16T10:00:30Z"), fixed.now())
    }
}
