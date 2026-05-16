package es.schsebastian.foodrats.feature.notifications.platform

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IosFcmTokenProvider : FcmTokenProvider {
    override val token: Flow<DeviceToken?> = flow {
        emit(Firebase.messaging.getToken()?.let(::DeviceToken))
    }
}
