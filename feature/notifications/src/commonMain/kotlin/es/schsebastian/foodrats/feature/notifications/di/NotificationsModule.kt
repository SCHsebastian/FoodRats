package es.schsebastian.foodrats.feature.notifications.di

import es.schsebastian.foodrats.feature.notifications.data.firebase.DeviceTokenFirestoreDataSource
import es.schsebastian.foodrats.feature.notifications.data.repository.DeviceTokenRepositoryImpl
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.usecase.HandleIncomingPushUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleStreakNudgeUseCase
import es.schsebastian.foodrats.feature.notifications.presentation.permission.NotificationPermissionViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val notificationsModule = module {
    singleOf(::NotificationBus)
    singleOf(::DeviceTokenFirestoreDataSource)
    single<DeviceTokenRepository> {
        DeviceTokenRepositoryImpl(get(), get(), get(), platformLabel = get(qualifier = org.koin.core.qualifier.named("platform-label")))
    }

    factoryOf(::RegisterDeviceTokenUseCase)
    factoryOf(::RequestNotificationPermissionUseCase)
    factory { ScheduleStreakNudgeUseCase(get(), get(), TimeZone.currentSystemDefault()) }
    factoryOf(::HandleIncomingPushUseCase)

    viewModelOf(::NotificationPermissionViewModel)
}
