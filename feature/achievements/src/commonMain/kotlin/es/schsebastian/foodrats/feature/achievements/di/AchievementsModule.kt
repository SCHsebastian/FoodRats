package es.schsebastian.foodrats.feature.achievements.di

import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressPort
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementErrorMapper
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementFirestoreDataSource
import es.schsebastian.foodrats.feature.achievements.data.firebase.AchievementUnlockStore
import es.schsebastian.foodrats.feature.achievements.data.repository.FirebaseAchievementRepository
import es.schsebastian.foodrats.feature.achievements.domain.AchievementEvaluator
import es.schsebastian.foodrats.feature.achievements.domain.AchievementReconciler
import es.schsebastian.foodrats.feature.achievements.domain.AchievementSignalsBuilder
import es.schsebastian.foodrats.feature.achievements.domain.usecase.ObserveAchievementsUseCase
import es.schsebastian.foodrats.feature.achievements.presentation.AchievementsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI graph for `:feature:achievements` (spec §14). This task (w2-badges-data) binds the
 * persistence port + the pure engine pieces. The presentation task (w2-badges-presentation) adds
 * `ObserveAchievementsUseCase` and the explicit `viewModel { ... }` (explicit, NOT viewModelOf, so
 * the `AnalyticsPort` default doesn't short-circuit graph resolution).
 *
 * `FirebaseFirestore` (GitLive) + `DispatcherProvider` are provided by `coreDataModule` — the same
 * source `:feature:crew`/`:feature:meal` consume.
 */
val achievementsModule = module {
    singleOf(::AchievementEvaluator)
    singleOf(::AchievementReconciler)
    singleOf(::AchievementSignalsBuilder)
    singleOf(::AchievementErrorMapper)
    single<AchievementUnlockStore> { AchievementFirestoreDataSource(get()) }
    single<AchievementProgressPort> { FirebaseAchievementRepository(get(), get(), get()) }
    factoryOf(::ObserveAchievementsUseCase)
    // Explicit viewModel (NOT viewModelOf): AchievementsViewModel.analytics has a NoopAnalyticsTracker
    // default; viewModelOf would short-circuit graph resolution and bind the no-op, so pass the real
    // AnalyticsPort by hand (CHARTER §9).
    viewModel {
        AchievementsViewModel(
            observeAchievements = get(),
            progress = get(),
            clock = get(),
            analytics = get(),
        )
    }
}
