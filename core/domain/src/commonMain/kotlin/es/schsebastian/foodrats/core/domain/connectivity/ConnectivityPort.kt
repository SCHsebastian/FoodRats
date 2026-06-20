package es.schsebastian.foodrats.core.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * App-wide connectivity signal for the offline-first machinery (plan §1).
 *
 * Emits `true` whenever the device has (validated) network and `false` when it
 * doesn't, including the current value on subscribe. Consumers observe the rising
 * edge (false → true) to drain pending work the moment connectivity returns — the
 * in-process counterpart to Android WorkManager's `NetworkType.CONNECTED` constraint.
 *
 * Promoted here from the meal feature so every context (outbox runner, sync engine,
 * offline banner) shares one signal instead of coupling an app-wide concern to one
 * feature. Platform implementations live in `:core:data`:
 * - Android: `ConnectivityManager.NetworkCallback` over a
 *   `NetworkCapabilities.NET_CAPABILITY_VALIDATED` request.
 * - iOS: `NWPathMonitor` (`nw_path_monitor`), satisfied-status → `true`.
 *
 * (DDD: a monitor that monitors is acceptable naming — it is not a banned
 * `Manager`/`Helper`/`Util`.)
 */
interface ConnectivityPort {
    /** Online state, hot, conflated to the latest value; emits the current value on subscribe. */
    fun isOnline(): Flow<Boolean>
}
