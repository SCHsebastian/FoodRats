package es.schsebastian.foodrats.feature.notifications.platform

import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosNotificationPermissionGateway : NotificationPermissionGateway {

    override suspend fun current(): NotificationPermission = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cont.resume(
                when (status) {
                    UNAuthorizationStatusAuthorized   -> NotificationPermission.Granted
                    UNAuthorizationStatusDenied       -> NotificationPermission.DeniedForever
                    UNAuthorizationStatusNotDetermined -> NotificationPermission.NotYetRequested
                    else                              -> NotificationPermission.Denied
                },
            )
        }
    }

    override suspend fun request(): NotificationPermission = suspendCancellableCoroutine { cont ->
        val opts = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(opts) { granted, _ ->
            cont.resume(if (granted) NotificationPermission.Granted else NotificationPermission.DeniedForever)
        }
    }

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        // Modern API — the deprecated single-arg openURL: is a silent no-op on recent iOS.
        UIApplication.sharedApplication.openURL(
            url = url,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
