package es.schsebastian.foodrats.feature.notifications.domain.repository

import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission

interface NotificationPermissionGateway {
    suspend fun current(): NotificationPermission
    suspend fun request(): NotificationPermission
    fun openSystemSettings()
}
