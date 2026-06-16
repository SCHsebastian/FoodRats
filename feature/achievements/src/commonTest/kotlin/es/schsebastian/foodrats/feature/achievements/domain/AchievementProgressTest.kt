package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementProgress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementProgressTest {

    @Test
    fun isMet_true_at_and_above_target() {
        assertTrue(AchievementProgress(current = 10, target = 10).isMet)
        assertTrue(AchievementProgress(current = 11, target = 10).isMet)
    }

    @Test
    fun isMet_false_below_target() {
        assertFalse(AchievementProgress(current = 9, target = 10).isMet)
    }

    @Test
    fun isMet_false_when_target_is_zero() {
        // Guards against a 0/0 row reading as "met".
        assertFalse(AchievementProgress(current = 0, target = 0).isMet)
    }
}
