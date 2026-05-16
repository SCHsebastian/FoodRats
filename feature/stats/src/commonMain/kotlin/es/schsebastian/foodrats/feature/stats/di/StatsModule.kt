package es.schsebastian.foodrats.feature.stats.di

import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val statsModule = module {
    factoryOf(::ObserveStatsUseCase)
    viewModelOf(::StatsViewModel)
}
