package es.schsebastian.foodrats.core.data.connectivity

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
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
 * iOS [ConnectivityPort] over `NWPathMonitor` (Network framework).
 *
 * Emits `true` while the current path status is `satisfied`. Consumers use the
 * false→true edge to drain pending work on reconnect. NOTE: this only fires while
 * the app is foreground/alive — iOS grants no general background execution without
 * `BGTaskScheduler` + entitlements, so an offline-composed plate publishes on the
 * next foreground reconnect, which is the documented best-effort iOS model.
 *
 * **Online semantics (IMPORTANT — differs from Android):** `NWPathMonitor` reports `satisfied`
 * when a network path is available, including behind a captive portal with no real internet. This
 * is a WEAKER signal than Android's `NET_CAPABILITY_VALIDATED`. Connectivity is therefore a HINT
 * to attempt a drain — the command's own success/failure (bounded by the retry policy) is the
 * source of truth, not the link state. A true reachability probe (e.g. a HEAD request to a known
 * endpoint) is deferred; the current iOS model is best-effort reconnect on path-satisfied.
 */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivityMonitor : ConnectivityPort {

    private val shared: Flow<Boolean> = rawIsOnline().shareIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 1,
    )

    override fun isOnline(): Flow<Boolean> = shared

    private fun rawIsOnline(): Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path: nw_path_t ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(0, 0u))
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.conflate().distinctUntilChanged()
}
