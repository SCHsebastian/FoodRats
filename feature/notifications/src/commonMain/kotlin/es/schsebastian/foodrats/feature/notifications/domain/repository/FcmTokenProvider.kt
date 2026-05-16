package es.schsebastian.foodrats.feature.notifications.domain.repository

import es.schsebastian.foodrats.feature.notifications.domain.model.DeviceToken
import kotlinx.coroutines.flow.Flow

/** Streams the current device's FCM token. Emits on init and on rotation. */
interface FcmTokenProvider {
    val token: Flow<DeviceToken?>
}
