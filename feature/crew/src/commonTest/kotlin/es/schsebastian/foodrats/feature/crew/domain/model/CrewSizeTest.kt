package es.schsebastian.foodrats.feature.crew.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrewSizeTest {
    @Test fun bounds() {
        assertEquals(3, CrewSize.MIN)
        assertEquals(8, CrewSize.MAX)
    }
    @Test fun canAdd_at_min_true() = assertTrue(CrewSize.canAdd(currentCount = 1))
    @Test fun canAdd_at_max_minus_one_true() = assertTrue(CrewSize.canAdd(currentCount = 7))
    @Test fun canAdd_at_max_false() = assertFalse(CrewSize.canAdd(currentCount = 8))
}
