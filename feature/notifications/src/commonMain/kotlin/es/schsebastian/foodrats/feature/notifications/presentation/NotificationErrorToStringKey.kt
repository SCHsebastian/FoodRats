package es.schsebastian.foodrats.feature.notifications.presentation

import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey

fun NotificationError.toStringKey(): NotificationStringKey = when (this) {
    NotificationError.Permission.Denied        -> NotificationStringKey.ErrorDenied
    NotificationError.Permission.DeniedForever -> NotificationStringKey.ErrorDeniedForever
    NotificationError.Permission.Unavailable   -> NotificationStringKey.ErrorUnavailable
    NotificationError.Token.Unavailable        -> NotificationStringKey.ErrorTokenUnavailable
    NotificationError.Token.PersistFailed      -> NotificationStringKey.ErrorTokenPersist
    NotificationError.Schedule.Failed          -> NotificationStringKey.ErrorScheduleFailed
}
