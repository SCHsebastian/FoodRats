package es.schsebastian.foodrats.feature.notifications.platform

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AndroidFcmTokenProvider : FcmTokenProvider {
    override val token: Flow<DeviceToken?> = flow {
        // GitLive exposes Firebase.messaging.getToken() (suspend) and token-refresh callbacks.
        // For MVP we emit the current token once; rotations will re-trigger via the receiver service.
        val current = Firebase.messaging.getToken()
        emit(current?.let(::DeviceToken))
    }
}
