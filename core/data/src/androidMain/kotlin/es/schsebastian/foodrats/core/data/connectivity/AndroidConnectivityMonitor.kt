package es.schsebastian.foodrats.core.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android [ConnectivityPort] over [ConnectivityManager.NetworkCallback].
 *
 * Emits the current online state on subscribe, then on every gain/loss of a
 * VALIDATED network. Consumers use the false→true edge; WorkManager's
 * `NetworkType.CONNECTED` constraint covers the after-process-death case.
 *
 * **Online semantics:** Android's `NET_CAPABILITY_VALIDATED` means the network has confirmed real
 * internet access (captive-portal check passed). This is a STRONG signal — online usually means the
 * network is genuinely reachable. Connectivity is still a HINT: use it to trigger a drain attempt,
 * but let the command's own success/failure (and retry policy) be the source of truth, not link state.
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityPort {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Flow<Boolean> = callbackFlow {
        fun snapshot(): Boolean {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        // Tracks every network currently offering VALIDATED internet; we're online iff the set is
        // non-empty. This replaces re-reading cm.activeNetwork inside onLost — at the instant onLost
        // fires, cm.activeNetwork can still return the tearing-down network as VALIDATED, so the old
        // snapshot() re-emitted `true` and distinctUntilChanged swallowed it: a live online→offline
        // transition never reported `false` (only a cold start while already offline did). Callbacks
        // for one registration are delivered serially on a single Handler thread, so a plain set is
        // safe here without synchronization.
        val validated = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun publish() { trySend(validated.isNotEmpty()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (online) validated.add(network) else validated.remove(network)
                publish()
            }
            override fun onLost(network: Network) { validated.remove(network); publish() }
            override fun onUnavailable() { publish() }
        }

        trySend(snapshot())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
