package es.schsebastian.foodrats.feature.feed.domain.usecase

import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: drives the online flag the offline-first use cases read via `isOnline().first()`. */
class FakeConnectivityPort(online: Boolean = true) : ConnectivityPort {
    val flow = MutableStateFlow(online)
    override fun isOnline(): Flow<Boolean> = flow
}
