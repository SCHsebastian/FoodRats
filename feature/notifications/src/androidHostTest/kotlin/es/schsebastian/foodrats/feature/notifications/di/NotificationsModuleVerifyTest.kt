package es.schsebastian.foodrats.feature.notifications.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [notificationsModule]: a missing or mis-typed binding fails here
 * instead of at app launch.
 *
 * [extraTypes] split three ways:
 *  - `:core:domain` ports / `:core:data` types wired app-wide: `SessionProvider`, `Clock`,
 *    `TimeZone`, `NotificationsPreferencePort`, plus the GitLive `FirebaseFirestore`.
 *  - Per-platform bindings the commonMain module consumes but only the androidMain/iosMain modules
 *    provide: `FcmTokenProvider`, `NotificationPermissionGateway`, `LocalReminderScheduler`. Listing
 *    them as extraTypes is the documented way to verify a commonMain module whose graph is completed
 *    per platform.
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class NotificationsModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun notifications_module_graph_is_complete() {
        notificationsModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                SessionProvider::class,
                Clock::class,
                TimeZone::class,
                NotificationsPreferencePort::class,
                FcmTokenProvider::class,
                NotificationPermissionGateway::class,
                LocalReminderScheduler::class,
                AnalyticsPort::class,
            ),
        )
    }
}
