package es.schsebastian.foodrats.feature.notifications.platform

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AndroidFcmTokenProvider(
    private val crashReporter: CrashReporter,
) : FcmTokenProvider {
    override val token: Flow<DeviceToken?> = flow {
        // GitLive exposes Firebase.messaging.getToken() (suspend) and token-refresh callbacks.
        // One-shot by design: each RegisterDeviceTokenUseCase invocation re-reads the CURRENT token.
        // Rotations re-trigger registration via FoodRatsFirebaseMessagingService.onNewToken.
        val current = try {
            Firebase.messaging.getToken()
        } catch (t: Throwable) {
            crashReporter.log("[AndroidFcmTokenProvider] FCM token unavailable: ${t.message}")
            null
        }
        emit(current?.let(::DeviceToken))
    }
}
