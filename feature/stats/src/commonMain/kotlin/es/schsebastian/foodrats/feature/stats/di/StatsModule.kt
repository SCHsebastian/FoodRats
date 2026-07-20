package es.schsebastian.foodrats.feature.stats.di

import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveMyMealCalendarUseCase
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import es.schsebastian.foodrats.feature.stats.presentation.calendar.MealCalendarViewModel
import es.schsebastian.foodrats.feature.stats.presentation.stats.StatsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsModule = module {
    // welcomePort is passed EXPLICITLY (C8b) so the NoopStatsWelcomePort constructor default never
    // short-circuits graph resolution — the Koin graph always provides the real implementation.
    factory {
        ObserveStatsUseCase(
            activeCrew = get(),
            session = get(),
            mealRead = get(),
            ingredientRead = get(),
            cuisineRead = get(),
            blockedAccounts = get(),
            clock = get(),
            zone = get(),
            welcomePort = get(),
        )
    }
    // "My plates" monthly calendar (own meals in the active crew, grouped by day).
    factory {
        ObserveMyMealCalendarUseCase(
            activeCrew = get(),
            session = get(),
            mealRead = get(),
            zone = get(),
        )
    }
    viewModel {
        MealCalendarViewModel(
            observeMyMealCalendar = get(),
            clock = get(),
            zone = get(),
        )
    }
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
