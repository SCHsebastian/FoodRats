package es.schsebastian.foodrats.app.di

import es.schsebastian.foodrats.app.root.RootNavViewModel
import es.schsebastian.foodrats.feature.auth.di.authModule
import es.schsebastian.foodrats.feature.crew.di.crewModule
import es.schsebastian.foodrats.feature.feed.di.feedModule
import es.schsebastian.foodrats.feature.meal.di.mealModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsModule
import es.schsebastian.foodrats.feature.stats.di.statsModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val rootNavModule = module {
    viewModelOf(::RootNavViewModel)
}

/** Modules common to both platforms. Platform-specific bridges live in `platformModule`. */
val appModules: List<org.koin.core.module.Module> = listOf(
    rootNavModule,
    coreDataModule,
    authModule,
    crewModule,
    mealModule,
    feedModule,
    statsModule,
    notificationsModule,
)
