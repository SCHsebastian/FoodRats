package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedModule = module {
    factoryOf(::ObserveFeedUseCase)
    // The time zone is platform-injected via Koin in the App Wiring plan
    // (commonly TimeZone.currentSystemDefault() resolved from a `TimeZoneProvider`).
    // For now we bind it inline so the module compiles standalone.
    single<TimeZone> { TimeZone.currentSystemDefault() }
    viewModel { FeedViewModel(get(), get(), get()) }
}
