package es.schsebastian.foodrats.feature.notifications.domain.usecase

import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway

class RequestNotificationPermissionUseCase(
    private val gateway: NotificationPermissionGateway,
) {
    suspend operator fun invoke(): NotificationPermission = gateway.request()
    suspend fun current(): NotificationPermission = gateway.current()
    fun openSettings() = gateway.openSystemSettings()
}
