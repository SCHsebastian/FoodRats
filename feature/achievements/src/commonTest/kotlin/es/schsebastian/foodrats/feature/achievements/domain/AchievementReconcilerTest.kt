package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.feature.achievements.domain.model.Achievement
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementId
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementProgress
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure reconcile (spec §6.3): overlay persisted unlock dates onto evaluator output and collect
 * the ids met-this-frame-but-not-yet-persisted. This is the engine→persistence seam the ViewModel
 * wires; testing it here proves "evaluator + persisted reconcile → correct earned set + unlock
 * timestamps; a new unlock is collected for persistence".
 */
class AchievementReconcilerTest {

    private val reconciler = AchievementReconciler()

    private fun catalogRow(id: String): Achievement =
        AchievementCatalog.all.first { it.id == AchievementId(id) }

    private fun status(id: String, current: Int, target: Int): AchievementStatus =
        AchievementStatus(catalogRow(id), AchievementProgress(current, target))

    @Test
    fun persisted_unlock_is_stamped_as_earned() {
        val evaluated = listOf(status("first_plate", current = 1, target = 1))
        val result = reconciler.reconcile(evaluated, persisted = mapOf("first_plate" to 100L), now = 999L)

        assertEquals(100L, result.statuses.single().unlockedAtEpochMs)
        assertTrue(result.newlyUnlocked.isEmpty(), "already-persisted achievement must not re-fire")
    }

    @Test
    fun newly_met_is_collected_with_now_and_left_unstamped_this_frame() {
        val evaluated = listOf(status("first_plate", current = 1, target = 1))
        val result = reconciler.reconcile(evaluated, persisted = emptyMap(), now = 777L)

        assertEquals(mapOf("first_plate" to 777L), result.newlyUnlocked)
        // Met-but-not-yet-written renders as locked-with-full-progress for one frame (no flicker).
        assertNull(result.statuses.single().unlockedAtEpochMs)
    }

    @Test
    fun locked_unmet_stays_locked_and_uncollected() {
        val evaluated = listOf(status("meals_10", current = 3, target = 10))
        val result = reconciler.reconcile(evaluated, persisted = emptyMap(), now = 1L)

        assertNull(result.statuses.single().unlockedAtEpochMs)
        assertTrue(result.newlyUnlocked.isEmpty())
    }

    @Test
    fun mixed_set_partitions_into_earned_newly_met_and_locked() {
        val evaluated = listOf(
            status("first_plate", current = 1, target = 1), // persisted → earned
            status("meals_10", current = 10, target = 10),  // newly met → collect
            status("meals_50", current = 12, target = 50),  // locked → ignore
        )
        val result = reconciler.reconcile(
            evaluated,
            persisted = mapOf("first_plate" to 100L),
            now = 500L,
        )

        val byId = result.statuses.associateBy { it.achievement.id.value }
        assertEquals(100L, byId.getValue("first_plate").unlockedAtEpochMs)
        assertNull(byId.getValue("meals_10").unlockedAtEpochMs)
        assertNull(byId.getValue("meals_50").unlockedAtEpochMs)
        assertEquals(mapOf("meals_10" to 500L), result.newlyUnlocked)
    }
}
