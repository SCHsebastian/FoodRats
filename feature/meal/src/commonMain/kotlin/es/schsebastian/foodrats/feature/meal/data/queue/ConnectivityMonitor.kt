package es.schsebastian.foodrats.feature.meal.data.queue

import kotlinx.coroutines.flow.Flow

/**
 * Minimal connectivity signal for the offline-first retry runner (roadmap §5.2).
 *
 * Emits `true` whenever the device has (validated) network and `false` when it
 * doesn't, including the current value on subscribe. The [DraftRetryRunner]
 * observes the rising edge (false → true) to drain the queue the moment
 * connectivity returns — the in-process counterpart to Android WorkManager's
 * `NetworkType.CONNECTED` constraint (which also re-triggers the worker on
 * reconnect after process death).
 *
 * Platform actuals:
 * - Android: `ConnectivityManager.NetworkCallback` over a
 *   `NetworkCapabilities.NET_CAPABILITY_VALIDATED` request.
 * - iOS: `NWPathMonitor` (`nw_path_monitor`), satisfied-status → `true`.
 */
interface ConnectivityMonitor {
    /** Online state, hot, conflated to the latest value; emits the current value on subscribe. */
    fun isOnline(): Flow<Boolean>
}
