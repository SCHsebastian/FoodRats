package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedModule = module {
    factoryOf(::ObserveFeedUseCase)
    single<TimeZone> { TimeZone.currentSystemDefault() }
    viewModel { FeedViewModel(get(), get(), get(), get(), get(), get()) }
}
