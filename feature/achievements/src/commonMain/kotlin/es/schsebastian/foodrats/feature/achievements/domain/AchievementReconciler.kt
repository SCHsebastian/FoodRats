package es.schsebastian.foodrats.feature.achievements.domain

import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus

/**
 * Pure overlay of persisted unlock dates onto a fresh evaluation (spec §6.3). Separated from the
 * pure [AchievementEvaluator] (which never sees persistence) and from the [FirebaseAchievementRepository]
 * (which never runs the engine) so the reconcile algorithm is unit-testable in isolation and the
 * ViewModel just wires `evaluate → reconcile → recordUnlocks`.
 *
 * For each evaluated [AchievementStatus]:
 *  - if its id is already in [persisted], stamp that timestamp → it renders as **earned**;
 *  - else if `progress.isMet`, it is **newly satisfied** → collect `id → now` (the caller persists
 *    these via `AchievementProgressPort.recordUnlocks`, then they flip to earned on the next
 *    snapshot). It is left with `unlockedAtEpochMs = null` for this frame — display reads that as
 *    locked-with-full-progress, no flicker because progress already reads 100% (spec §6.3);
 *  - else it stays locked (`unlockedAtEpochMs = null`).
 */
class AchievementReconciler {

    data class Reconciled(
        /** Every status, with persisted unlock dates overlaid. */
        val statuses: List<AchievementStatus>,
        /** Ids met this frame but not yet persisted → their unlock time (raw id → [now]). */
        val newlyUnlocked: Map<String, Long>,
    )

    fun reconcile(
        evaluated: List<AchievementStatus>,
        persisted: Map<String, Long>,
        now: Long,
    ): Reconciled {
        val newlyUnlocked = mutableMapOf<String, Long>()
        val statuses = evaluated.map { status ->
            val id = status.achievement.id.value
            val persistedAt = persisted[id]
            when {
                persistedAt != null -> status.copy(unlockedAtEpochMs = persistedAt)
                status.progress.isMet -> {
                    newlyUnlocked[id] = now
                    status
                }
                else -> status
            }
        }
        return Reconciled(statuses, newlyUnlocked)
    }
}
