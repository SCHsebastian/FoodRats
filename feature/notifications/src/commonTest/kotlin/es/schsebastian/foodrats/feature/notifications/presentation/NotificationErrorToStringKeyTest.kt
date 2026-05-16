package es.schsebastian.foodrats.feature.notifications.presentation

import es.schsebastian.foodrats.feature.notifications.domain.error.NotificationError
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationErrorToStringKeyTest {
    @Test fun permissionDenied()        = assertEquals(NotificationStringKey.ErrorDenied, NotificationError.Permission.Denied.toStringKey())
    @Test fun permissionDeniedForever() = assertEquals(NotificationStringKey.ErrorDeniedForever, NotificationError.Permission.DeniedForever.toStringKey())
    @Test fun permissionUnavailable()   = assertEquals(NotificationStringKey.ErrorUnavailable, NotificationError.Permission.Unavailable.toStringKey())
    @Test fun tokenUnavailable()        = assertEquals(NotificationStringKey.ErrorTokenUnavailable, NotificationError.Token.Unavailable.toStringKey())
    @Test fun tokenPersist()            = assertEquals(NotificationStringKey.ErrorTokenPersist, NotificationError.Token.PersistFailed.toStringKey())
    @Test fun scheduleFailed()          = assertEquals(NotificationStringKey.ErrorScheduleFailed, NotificationError.Schedule.Failed.toStringKey())
}
