package es.schsebastian.foodrats.feature.notifications.di

import android.app.Activity
import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.platform.AndroidFcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.platform.AndroidLocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.platform.AndroidNotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.platform.PermissionLauncherHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val notificationsAndroidModule = module {
    single(named("platform-label")) { "android" }
    singleOf(::PermissionLauncherHolder)
    single<NotificationPermissionGateway> {
        AndroidNotificationPermissionGateway(
            appContext = androidContext(),
            activityProvider = { get<() -> Activity?>().invoke() },   // see App Wiring plan for the lambda binding
            launcherHolder = get(),
        )
    }
    single<LocalReminderScheduler> { AndroidLocalReminderScheduler(androidContext(), get()) }
    single<FcmTokenProvider> { AndroidFcmTokenProvider() }
}
