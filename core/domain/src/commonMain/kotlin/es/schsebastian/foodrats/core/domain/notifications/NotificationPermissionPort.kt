package es.schsebastian.foodrats.core.domain.notifications

/**
 * Port over the OS notification authorization API. Declared in :core:domain so features
 * outside :feature:notifications (e.g. :feature:auth's settings screen) can request the
 * OS permission without taking a hard dependency on the notifications feature.
 *
 * The adapter lives in :feature:notifications and wraps the existing
 * `NotificationPermissionGateway`.
 */
interface NotificationPermissionPort {
    suspend fun current(): NotificationPermissionStatus
    suspend fun request(): NotificationPermissionStatus
    fun openSystemSettings()
}

enum class NotificationPermissionStatus { NotYetRequested, Granted, Denied, DeniedForever }
