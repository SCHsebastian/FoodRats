package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailViewModel
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedModule = module {
    factoryOf(::ObserveFeedUseCase)
    factoryOf(::DeleteMealUseCase)
    factoryOf(::DeleteCommentUseCase)
    // TimeZone is bound once in coreDataModule (loaded app-wide); feed resolves it from there.
    viewModel { FeedViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (mealId: String, dayIso: String) ->
        MealDetailViewModel(
            mealId, dayIso,
            get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(),
        )
    }
}
