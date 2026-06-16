package es.schsebastian.foodrats.feature.meal.data.queue

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.dispatch_get_global_queue

/**
 * iOS [ConnectivityMonitor] over `NWPathMonitor` (Network framework).
 *
 * Emits `true` while the current path status is `satisfied`. The retry runner
 * uses the false→true edge to drain the queue on reconnect. NOTE: this only
 * fires while the app is foreground/alive — iOS grants no general background
 * execution without `BGTaskScheduler` + entitlements (see
 * `InProcessMealUploadScheduler`), so an offline-composed plate publishes on the
 * next foreground reconnect, which is the documented best-effort iOS model.
 */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivityMonitor : ConnectivityMonitor {

    override fun isOnline(): Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path: nw_path_t ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(0, 0u))
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.conflate().distinctUntilChanged()
}
