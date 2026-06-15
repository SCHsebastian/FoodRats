package es.schsebastian.foodrats.feature.meal.domain.queue

import es.schsebastian.foodrats.core.domain.meal.MealId
import es.schsebastian.foodrats.feature.meal.domain.model.MealDraft

/**
 * Idempotency-key strategy for the offline-first publish queue (roadmap §5.2).
 *
 * A queued draft is published once per crew in its audience, and each per-crew
 * publish is made idempotent by a *deterministic* meal id derived from
 * `(crewId, authorId, day, slot)` — [MealId.forDaySlot]. Re-publishing the same
 * draft (after a partial failure, a crash, or a WorkManager retry) targets the
 * exact same document id (and the same deterministic Storage path), so the write
 * overwrites rather than duplicates. There is no separate idempotency token to
 * persist or transmit: the meal id IS the idempotency key.
 *
 * This is a pure, total function (returns the same keys for the same draft every
 * call) so the queue's idempotency contract can be unit-tested for stability.
 *
 * @return the set of per-crew deterministic meal ids this draft will publish to.
 *   Empty only if the draft's audience is empty (which `PublishMealUseCase`
 *   rejects with `MealError.Publish.NoCrewSelected` — so a queued draft should
 *   never have an empty audience), or if the draft has no [MealDraft.slot]
 *   selected yet (no stable id can be derived without a slot).
 */
fun MealDraft.idempotencyKeys(): Set<MealId> {
    val resolvedSlot = slot ?: return emptySet()
    return audienceCrewIds
        .map { crewId -> MealId.forDaySlot(crewId, authorId, day, resolvedSlot) }
        .toSet()
}
