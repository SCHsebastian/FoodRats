package es.schsebastian.foodrats.feature.notifications.di

import es.schsebastian.foodrats.feature.notifications.domain.repository.FcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.domain.repository.LocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.platform.IosFcmTokenProvider
import es.schsebastian.foodrats.feature.notifications.platform.IosLocalReminderScheduler
import es.schsebastian.foodrats.feature.notifications.platform.IosNotificationPermissionGateway
import org.koin.core.qualifier.named
import org.koin.dsl.module

val notificationsIosModule = module {
    single(named("platform-label")) { "ios" }
    single<NotificationPermissionGateway> { IosNotificationPermissionGateway() }
    single<LocalReminderScheduler> { IosLocalReminderScheduler() }
    single<FcmTokenProvider> { IosFcmTokenProvider() }
}
