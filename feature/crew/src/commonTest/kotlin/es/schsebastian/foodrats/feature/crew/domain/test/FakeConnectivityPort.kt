package es.schsebastian.foodrats.feature.crew.domain.test

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: drives the online flag the offline-first crew use cases read via `isOnline().first()`. */
class FakeConnectivityPort(online: Boolean = true) : ConnectivityPort {
    val flow = MutableStateFlow(online)
    override fun isOnline(): Flow<Boolean> = flow
}
