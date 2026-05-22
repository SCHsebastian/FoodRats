package es.schsebastian.foodrats.feature.notifications.data.adapter

import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway

/**
 * Adapter that exposes the feature-local [NotificationPermissionGateway] through the
 * cross-feature [NotificationPermissionPort] declared in :core:domain. Lets the settings
 * screen (in :feature:auth) drive the OS prompt without depending on :feature:notifications.
 */
class NotificationPermissionAdapter(
    private val gateway: NotificationPermissionGateway,
) : NotificationPermissionPort {
    override suspend fun current(): NotificationPermissionStatus = gateway.current().toStatus()
    override suspend fun request(): NotificationPermissionStatus = gateway.request().toStatus()
    override fun openSystemSettings() = gateway.openSystemSettings()

    private fun NotificationPermission.toStatus(): NotificationPermissionStatus = when (this) {
        NotificationPermission.NotYetRequested -> NotificationPermissionStatus.NotYetRequested
        NotificationPermission.Granted         -> NotificationPermissionStatus.Granted
        NotificationPermission.Denied          -> NotificationPermissionStatus.Denied
        NotificationPermission.DeniedForever   -> NotificationPermissionStatus.DeniedForever
    }
}
