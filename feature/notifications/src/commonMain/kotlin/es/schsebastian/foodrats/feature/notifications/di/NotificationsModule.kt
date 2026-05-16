package es.schsebastian.foodrats.feature.notifications.di

import org.koin.dsl.module

val notificationsModule = module {
    // Scaffold only. Platform actuals (FCM, permission gateways) live in
    // androidMain / iosMain source sets and are wired by androidApp/iosApp.
}
