package es.schsebastian.foodrats.feature.stats.di

import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsModule = module {
    factoryOf(::ObserveStatsUseCase)
    // analytics is passed EXPLICITLY (CHARTER §9) so the NoopAnalyticsTracker default never
    // short-circuits graph resolution; storyShareController is the share seam (decode→render→launch).
    viewModel {
        StatsViewModel(
            observeStats = get(),
            uploadProgress = get(),
            storyShareController = get(),
            clock = get(),
            zone = get(),
            analytics = get(),
        )
    }
}
