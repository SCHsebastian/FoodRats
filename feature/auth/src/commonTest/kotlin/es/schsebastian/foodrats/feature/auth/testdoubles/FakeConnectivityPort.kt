package es.schsebastian.foodrats.feature.auth.testdoubles

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Drives the online flag the offline-first profile use cases read via `isOnline().first()`. */
class FakeConnectivityPort(online: Boolean = true) : ConnectivityPort {
    val flow = MutableStateFlow(online)
    override fun isOnline(): Flow<Boolean> = flow
}
