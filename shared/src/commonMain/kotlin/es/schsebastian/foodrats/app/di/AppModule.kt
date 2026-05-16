package es.schsebastian.foodrats.app.di

import es.schsebastian.foodrats.feature.auth.di.authModule
import es.schsebastian.foodrats.feature.crew.di.crewModule
import es.schsebastian.foodrats.feature.feed.di.feedModule
import es.schsebastian.foodrats.feature.meal.di.mealModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsModule
import es.schsebastian.foodrats.feature.stats.di.statsModule

val appModules = listOf(
    coreDataModule,
    authModule,
    crewModule,
    mealModule,
    feedModule,
    statsModule,
    notificationsModule,
)
