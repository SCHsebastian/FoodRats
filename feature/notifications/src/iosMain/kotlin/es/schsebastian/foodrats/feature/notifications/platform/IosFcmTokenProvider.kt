package es.schsebastian.foodrats.feature.notifications.platform

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class IosFcmTokenProvider : FcmTokenProvider {
    override val token: Flow<DeviceToken?> = flow {
        // FCM token retrieval requires APNs to be set up (Apple Developer Push Notifications
        // entitlement + APNs key in Firebase Console). On dev iPhones / personal-team builds
        // without that setup, getToken() throws `No APNS token specified...`. Swallow it and
        // emit null — the post-signin token registration is fire-and-forget, the rest of the
        // app shouldn't crash because push isn't configured yet.
        val token = try {
            Firebase.messaging.getToken()
        } catch (t: Throwable) {
            println("[IosFcmTokenProvider] FCM token unavailable: ${t.message}")
            null
        }
        emit(token?.let(::DeviceToken))
    }
}
