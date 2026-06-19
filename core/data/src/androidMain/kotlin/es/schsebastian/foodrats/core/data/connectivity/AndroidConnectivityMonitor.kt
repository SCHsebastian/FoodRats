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
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityPort {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Flow<Boolean> = callbackFlow {
        fun snapshot(): Boolean {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(snapshot()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                )
            }
        }

        trySend(snapshot())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
