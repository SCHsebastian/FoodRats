package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailViewModel
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val feedModule = module {
    factoryOf(::ObserveFeedUseCase)
    factoryOf(::RateMealUseCase)
    factoryOf(::DeleteMealUseCase)
    factoryOf(::DeleteMyMealUseCase)
    factoryOf(::DeleteCommentUseCase)
    // TimeZone is bound once in coreDataModule (loaded app-wide); feed resolves it from there.
    // analytics is passed EXPLICITLY (CHARTER rule 9) so the NoopAnalyticsTracker default never
    // short-circuits graph resolution — same reason MealReactionPort is bound positionally here.
    viewModel {
        FeedViewModel(
            observeFeed = get(),
            rateMeal = get(),
            activeCrew = get(),
            session = get(),
            clock = get(),
            zone = get(),
            uploadProgress = get(),
            blindVoting = get(),
            reactions = get(),
            queuedUploadActions = get(),
            connectivity = get(),
            outbox = get(),
            syncStatus = get(),
            analytics = get(),
        )
    }
    viewModel { (mealId: String, dayIso: String) ->
        MealDetailViewModel(
            mealId = mealId,
            dayIso = dayIso,
            observeFeed = get(),
            rateMeal = get(),
            commentPort = get(),
            connectivity = get(),
            outbox = get(),
            accountReadPort = get(),
            ingredientRead = get(),
            activeCrew = get(),
            blindVoting = get(),
            session = get(),
            clock = get(),
            zone = get(),
            deleteMeal = get(),
            deleteMyMeal = get(),
            deleteComment = get(),
            crewOwner = get(),
            storyShareController = get(),
            analytics = get(),
        )
    }
}
