package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Cross-context read seam (offline-first P4-T2) exposing how FRESH a crew's cached feed is, plus a
 * manual re-pull. The meal sync engine (`:feature:meal`) owns the rolling-window mirror that keeps
 * the local feed DB current; this port lets `:feature:feed` show a "synced X ago" line and offer a
 * pull-to-refresh WITHOUT depending on `:feature:meal`'s engine internals.
 *
 * Implemented by `:feature:meal` over its `MealSyncEngine`; declared here so the feed ViewModel
 * depends on a `:core:domain` contract, not a feature internal — same pattern as
 * [OptimisticMealWritePort] / [MealReadPort].
 */
interface FeedSyncStatusPort {

    /**
     * The wall-clock instant of the LAST successful window write for [crewId], or `null` if this
     * crew has never synced this session (app-lifetime, in-memory — not durable across process
     * death). Re-emits each time the engine folds a fresh server snapshot into the local store.
     */
    fun lastSyncedAt(crewId: CrewId): Flow<Instant?>

    /**
     * Forces a re-pull of [crewId]'s window: cancels and restarts the per-crew sync job so the next
     * server snapshot is re-fetched and re-stamped. Idempotent — a no-op beyond re-arming the live
     * listener. Returns once the restart has been kicked off (not once the snapshot lands).
     */
    suspend fun refresh(crewId: CrewId)
}
