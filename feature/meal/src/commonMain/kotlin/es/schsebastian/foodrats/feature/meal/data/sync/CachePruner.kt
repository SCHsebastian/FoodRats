package es.schsebastian.foodrats.feature.meal.data.sync

import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.meal.data.local.MealLocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/**
 * Bounds local DB growth (offline-first P4-T1). [MealSyncEngine]'s delete-by-absence only spans the
 * rolling 30-day sync window, so meals that age out of the window are never deleted and accumulate
 * forever. This pruner runs ONCE at app start on the app-lifetime [appScope]
 * ([named("appScope")][org.koin.core.qualifier.named]) and drops every meal older than
 * [RETENTION_DAYS] (keeping comfortably more than the 30-day window so stats history survives).
 * Ratings of pruned meals cascade away via the `mealRating` FK.
 *
 * It owns NO IO boundary: [MealLocalStore.pruneOlderThan] owns its single `withContext(io)`; this is
 * pure orchestration. Bound `createdAtStart = true` in `mealModule`, like [MealSyncEngine].
 */
internal class CachePruner(
    private val local: MealLocalStore,
    private val clock: Clock,
    private val zone: TimeZone,
    private val appScope: CoroutineScope,
) {
    private companion object {
        // 90 days >> the 30-day sync window: stats history (last 30 days) is always retained, with a
        // wide margin so a clock skew or an off-by-a-few-days never prunes a row stats still needs.
        const val RETENTION_DAYS = 90
    }

    /**
     * Computes the retention cutoff from [clock] and prunes meals strictly older than it. The cutoff
     * key is `today - RETENTION_DAYS` as a `YYYY-MM-DD` day key; [MealLocalStore.pruneOlderThan]
     * deletes rows with `dayKey < cutoff`. Fire-and-forget on [appScope]; a failure is logged, never
     * thrown — pruning is best-effort housekeeping and must not crash boot.
     */
    fun start() {
        appScope.launch {
            val today = MealDay.today(clock, zone)
            val cutoff = MealDay(today.date.minus(DatePeriod(days = RETENTION_DAYS)), zone)
            runCatching { local.pruneOlderThan(cutoff.toKey()) }
                .onFailure { FrLog.w("CachePruner", it) { "prune failed: ${it.message}" } }
        }
    }
}
