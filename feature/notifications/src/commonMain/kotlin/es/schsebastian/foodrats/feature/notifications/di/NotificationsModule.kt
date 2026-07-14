package es.schsebastian.foodrats.feature.notifications.di

import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.StreakNotificationPort
import es.schsebastian.foodrats.core.domain.notifications.TokenRegistrationPort
import es.schsebastian.foodrats.feature.notifications.data.adapter.DeviceTokenLanguageSync
import es.schsebastian.foodrats.feature.notifications.data.adapter.MealReminderScheduler
import es.schsebastian.foodrats.feature.notifications.data.adapter.NotificationPermissionAdapter
import es.schsebastian.foodrats.feature.notifications.data.adapter.StreakNotificationAdapter
import es.schsebastian.foodrats.feature.notifications.data.adapter.TokenRegistrationAdapter
import es.schsebastian.foodrats.feature.notifications.data.firebase.DeviceTokenFirestoreDataSource
import es.schsebastian.foodrats.feature.notifications.data.locale.AppLanguageTag
import es.schsebastian.foodrats.feature.notifications.data.push.PushPayloadMapper
import es.schsebastian.foodrats.feature.notifications.data.repository.DeviceTokenRepositoryImpl
import es.schsebastian.foodrats.feature.notifications.domain.bus.NotificationBus
import es.schsebastian.foodrats.feature.notifications.domain.repository.DeviceTokenRepository
import es.schsebastian.foodrats.feature.notifications.domain.repository.EffectiveLanguageTag
import es.schsebastian.foodrats.feature.notifications.domain.usecase.DeregisterDeviceTokenUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RegisterDeviceTokenUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import es.schsebastian.foodrats.feature.notifications.domain.usecase.ScheduleDailyInactivityReminderUseCase
import es.schsebastian.foodrats.feature.notifications.presentation.permission.NotificationPermissionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val notificationsModule = module {
    singleOf(::NotificationBus)
    singleOf(::DeviceTokenFirestoreDataSource)
    single<DeviceTokenRepository> {
        DeviceTokenRepositoryImpl(get(), get(), get(), platformLabel = get(qualifier = named("platform-label")))
    }
    single { PushPayloadMapper(clock = get()) }

    // Resolves the effective UI language (in-app override, else OS) for the device-token doc.
    single<EffectiveLanguageTag> { AppLanguageTag(localePort = get()) }

    factoryOf(::RegisterDeviceTokenUseCase)
    factoryOf(::DeregisterDeviceTokenUseCase)
    factoryOf(::RequestNotificationPermissionUseCase)
    factory { ScheduleDailyInactivityReminderUseCase(get(), get(), TimeZone.currentSystemDefault()) }

    // Cross-feature ports — :feature:auth and :feature:meal call these instead of importing
    // this feature directly.
    single<TokenRegistrationPort> { TokenRegistrationAdapter(get(), get()) }
    single<StreakNotificationPort> { StreakNotificationAdapter(get()) }
    single<NotificationPermissionPort> { NotificationPermissionAdapter(get()) }

    // App-lifetime scope for the reactive reminder scheduler below.
    single<CoroutineScope>(named("notif-app-scope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    // Eager: starts at app launch and keeps the OS reminders in sync with the persisted times +
    // notifications opt-in. This is what re-establishes (and re-times) the daily meal reminders.
    single(createdAtStart = true) {
        MealReminderScheduler(
            scope = get(named("notif-app-scope")),
            schedulePort = get(),
            notificationsPref = get(),
            useCase = get(),
            localScheduler = get(),
        )
    }

    // Eager: re-stamps the device token's languageTag on sign-in and on every in-app language
    // change, so the server localizes this device's OS notifications to the current language.
    single(createdAtStart = true) {
        DeviceTokenLanguageSync(
            scope = get(named("notif-app-scope")),
            session = get(),
            localePort = get(),
            register = get(),
        )
    }

    viewModel { NotificationPermissionViewModel(uc = get(), prefs = get(), analytics = get()) }
}
