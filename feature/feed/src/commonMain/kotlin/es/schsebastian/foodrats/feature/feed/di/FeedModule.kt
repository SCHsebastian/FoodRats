package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.LocalePort
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteCommentUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.DeleteMyMealUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.ObserveFeedUseCase
import es.schsebastian.foodrats.feature.feed.domain.usecase.RateMealUseCase
import es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailViewModel
import es.schsebastian.foodrats.feature.feed.presentation.feed.FeedViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the BCP-47 active-language flow the on-device comment filter reads (UGC §3). */
private val ModerationLanguageTag = named("feedModerationLanguageTag")

val feedModule = module {
    // Advisory language hint for the on-device comment text filter. The default filter screens ALL
    // supported languages regardless of this tag (see WordlistTextModeration / Wordlists.ALL), so a
    // System/English-locale device still screens Spanish (and any other supported language) abuse.
    single<Flow<String>>(ModerationLanguageTag) {
        get<LocalePort>().locale.map { if (it == AppLocale.System) "en" else it.tag }
    }
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
            optimistic = get(),
            // UGC compliance §4/§5 — passed EXPLICITLY (the VM ctor defaults are test-only no-ops).
            reportPort = get(),
            blockedAccounts = get(),
            analytics = get(),
            // C6 — welcome banner; passed EXPLICITLY so the noop default never short-circuits.
            welcomePort = get(),
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
            // UGC compliance §3/§4/§5 — passed EXPLICITLY (the VM ctor defaults are test-only no-ops).
            textModeration = get(),
            languageTag = get<Flow<String>>(ModerationLanguageTag),
            reportPort = get(),
            blockedAccounts = get(),
            analytics = get(),
        )
    }
}
