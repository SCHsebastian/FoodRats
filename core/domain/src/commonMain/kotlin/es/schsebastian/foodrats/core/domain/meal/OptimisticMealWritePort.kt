package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * Cross-context write seam (offline-first P3b §P3b-T5) that lets a feature OUTSIDE `:feature:meal`
 * apply an OPTIMISTIC mutation directly into the meal feed's local read source-of-truth (the
 * SQLDelight `meal` + `mealRating` tables owned by `:feature:meal`). The feed renders straight from
 * that store, so writing the optimistic row here makes the change visible instantly — even though
 * `:feature:feed` cannot (and must not) depend on `:feature:meal`'s `MealLocalStore`.
 *
 * Bounded to RATE only for now: reactions/comments optimism is deferred. The optimistic row is
 * marked `pending = 1` and the touched meal records the command's [idempotencyKey], so the existing
 * meal SYNC path (which (re)writes `mealRating` rows from each server snapshot with `pending = 0`)
 * overwrites the optimistic row with server truth when the rated meal syncs back — auto-clearing the
 * pending flag without any explicit reconciliation call.
 *
 * Implemented by `:feature:meal` over its `MealLocalStore`; declared here so the use case depends on
 * a `:core:domain` contract, not a feature internal. Methods are side-effecting local writes (no
 * network), each with its own IO boundary in the implementation; they never fail the user-facing
 * flow — a local-write hiccup must not block an offline rate, which is already durably queued.
 */
interface OptimisticMealWritePort {

    /**
     * Records [raterId]'s [score] for [mealId] in the local store as a PENDING row: upsert a
     * `mealRating(pending = 1)`, recompute the meal's denormalized `ratingSum`/`voterCount` from the
     * resulting rating set, and stamp the meal `pending = 1` + its [idempotencyKey]. A no-op if the
     * meal isn't held locally (nothing to render optimistically against). Idempotent on
     * (mealId, raterId): re-rating overwrites the same row.
     */
    suspend fun applyRate(
        crewId: CrewId,
        mealId: MealId,
        raterId: AccountId,
        score: Score,
        idempotencyKey: String,
    )

    /**
     * Clears the optimistic bookkeeping for the meal carrying [idempotencyKey] (meal `pending = 0`,
     * `idempotencyKey = null`) and drops any `pending = 1` rating rows on it — used to ROLL BACK a
     * terminally-failed optimistic write. A no-op if no meal carries that key (the server snapshot
     * already reconciled it). Server-confirmed (`pending = 0`) rating rows are left intact.
     */
    suspend fun clearPending(idempotencyKey: String)
}
