package es.schsebastian.foodrats.app.di

import es.schsebastian.foodrats.app.connectivity.ConnectivityViewModel
import es.schsebastian.foodrats.app.consent.ConsentViewModel
import es.schsebastian.foodrats.app.navigation.DeepLinkBus
import es.schsebastian.foodrats.app.recap.weeklyStoryModule
import es.schsebastian.foodrats.app.root.RootNavViewModel
import es.schsebastian.foodrats.core.data.outbox.outboxModule
import es.schsebastian.foodrats.core.database.di.databaseModule
import es.schsebastian.foodrats.feature.achievements.di.achievementsModule
import es.schsebastian.foodrats.feature.auth.di.authModule
import es.schsebastian.foodrats.feature.crew.di.crewModule
import es.schsebastian.foodrats.feature.feed.di.feedModule
import es.schsebastian.foodrats.feature.ingredient.di.cuisineModule
import es.schsebastian.foodrats.feature.ingredient.di.ingredientModule
import es.schsebastian.foodrats.feature.meal.di.mealModule
import es.schsebastian.foodrats.feature.moderation.di.moderationModule
import es.schsebastian.foodrats.feature.notifications.di.notificationsModule
import es.schsebastian.foodrats.feature.stats.di.statsModule
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private val rootNavModule = module {
    // App-lifetime conduit for external URIs; published to by platform entry points
    // (Android MainActivity, iOS IosDeepLinkBridge), consumed by RootNavViewModel.
    single { DeepLinkBus() }
    viewModelOf(::RootNavViewModel)
    // App-wide offline banner (offline-first §P1-T2): projects ConnectivityPort.isOnline (bound per
    // platform in connectivity{Android,Ios}Module) into a StateFlow for the root NavHost.
    viewModelOf(::ConnectivityViewModel)
    // Explicit binding (NOT viewModelOf): ConsentViewModel.analytics has a NoopAnalyticsTracker
    // default; viewModelOf would short-circuit graph resolution and bind the no-op, so we pass the
    // real AnalyticsPort by hand — same pattern the feature *Module files use for analytics.
    viewModel { ConsentViewModel(consent = get(), analytics = get()) }
}

private val recapModule = weeklyStoryModule

/** Modules common to both platforms. Platform-specific bridges live in `platformModule`. */
val appModules: List<org.koin.core.module.Module> = listOf(
    rootNavModule,
    recapModule,
    coreDataModule,
    // Offline-first local read source-of-truth (P3a §2): builds FoodRatsDatabase over the
    // per-platform SqlDriver (bound in databaseAndroidModule / databaseIosModule). After
    // coreDataModule so its DispatcherProvider et al. are available to local stores built on top.
    databaseModule,
    // Offline-first write outbox (P2 §1 T4). Coexists with the meal-publish queue
    // (DraftQueue*, in mealModule). The eager OutboxRunner drains it on the appScope;
    // feature handlers (meal/crew) are collected via Koin getAll().
    outboxModule,
    authModule,
    crewModule,
    mealModule,
    feedModule,
    statsModule,
    // UGC compliance (§7): block list + report queue ports over Firestore. Consumed by feed/stats/meal
    // through the :core:domain ports (BlockedAccountsPort, ReportPort), so no feature depends on it.
    moderationModule,
    notificationsModule,
    // Catalog + picker (:feature:ingredient). The MealClassifierPort is bound per
    // platform: mealAiAndroidModule (FoodRatsApplication) / mealAiIosModule (MainViewController).
    ingredientModule,
    // Cuisine catalog read path (CuisineReadPort), bound in :feature:ingredient over the same
    // app-scope as ingredientModule. Feeds the meal publish cuisine stamp + the stats passport grid.
    cuisineModule,
    // Badges & achievements engine + unlock-timestamp persistence (AchievementProgressPort). The
    // presentation task adds the use case + viewModel into achievementsModule.
    achievementsModule,
)
