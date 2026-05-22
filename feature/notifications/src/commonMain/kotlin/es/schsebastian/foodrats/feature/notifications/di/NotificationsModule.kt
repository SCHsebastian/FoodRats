package es.schsebastian.foodrats.feature.notifications.di

import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.feature.notifications.data.adapter.StreakNotificationAdapter
import es.schsebastian.foodrats.feature.notifications.data.adapter.TokenRegistrationAdapter
import es.schsebastian.foodrats.feature.notifications.data.firebase.DeviceTokenFirestoreDataSource
import es.schsebastian.foodrats.feature.notifications.data.push.PushPayloadMapper
import es.schsebastian.foodrats.feature.notifications.data.repository.DeviceTokenRepositoryImpl
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.usecase.HandleIncomingPushUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
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
    single { PushPayloadMapper(clock = get()) }

    factoryOf(::RegisterDeviceTokenUseCase)
    factoryOf(::RequestNotificationPermissionUseCase)
    factory { ScheduleDailyInactivityReminderUseCase(get(), get(), TimeZone.currentSystemDefault()) }
    factoryOf(::HandleIncomingPushUseCase)

    // Cross-feature ports — :feature:auth and :feature:meal call these instead of importing
    // this feature directly.
    single<TokenRegistrationPort> { TokenRegistrationAdapter(get()) }
    single<StreakNotificationPort> { StreakNotificationAdapter(get()) }

    viewModelOf(::NotificationPermissionViewModel)
}
