package es.schsebastian.foodrats.feature.auth.di

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeModePort
import es.schsebastian.foodrats.core.domain.telemetry.CrashReporter
import es.schsebastian.foodrats.core.domain.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [authModule]: a missing or mis-typed binding fails here instead of at
 * app launch.
 *
 * [extraTypes] are the cross-module dependencies auth consumes but does not bind: the GitLive
 * Firebase handles (`FirebaseAuth`/`FirebaseFirestore`/`FirebaseStorage`, app-wide), `:core:domain`
 * infra (`Clock`, `DispatcherProvider`, `CrashReporter`), the preference ports (`ThemeModePort`,
 * `LocalePort`, `NotificationsPreferencePort` — bound in `coreDataModule`), and the notifications
 * cross-feature ports (`TokenRegistrationPort`, `NotificationPermissionPort`).
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class AuthModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun auth_module_graph_is_complete() {
        authModule.verify(
            extraTypes = listOf(
                FirebaseAuth::class,
                FirebaseFirestore::class,
                FirebaseStorage::class,
                Clock::class,
                DispatcherProvider::class,
                CrashReporter::class,
                ThemeModePort::class,
                LocalePort::class,
                NotificationsPreferencePort::class,
                TokenRegistrationPort::class,
                NotificationPermissionPort::class,
            ),
        )
    }
}
