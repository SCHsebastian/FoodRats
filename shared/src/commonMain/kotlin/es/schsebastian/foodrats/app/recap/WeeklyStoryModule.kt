package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI for the weekly-recap story player (roadmap §2.4). It lives in `shared` — NOT in `:feature:stats`
 * — because the recap is assembled from BOTH `ObserveStatsUseCase` (`:feature:stats`) and
 * `ObserveAchievementsUseCase` (`:feature:achievements`), and features may not depend on each other
 * (CHARTER rule 2). `shared` is the only place both use cases co-exist (it already aggregates every
 * feature module + hosts the cross-feature app UI like ConsentScreen).
 *
 * The [WeeklyRecapStream] is the recap read seam (adapts the two concrete use cases through the pure
 * assembler), keeping the ViewModel unit-testable behind a trivial fake. The viewModel is explicit
 * (NOT viewModelOf): [WeeklyStoryViewModel.analytics] has a NoopAnalyticsTracker default, and
 * `viewModelOf` would short-circuit graph resolution and bind the no-op (CHARTER rule 9).
 * [DigestStorySource] is a runtime parameter — the screen passes it via `parametersOf(...)` so the
 * open-analytics event records notification-tap vs. in-app entry.
 *
 * `activeCrew`/`session`/`mealRead` (TRACK B photo floors) resolve to whichever `ActiveCrewProvider` /
 * `SessionProvider` / `MealReadPort` bindings the aggregated feature graph already provides (the same
 * ones `ObserveStatsUseCase` consumes) — `shared` doesn't rebind them.
 */
val weeklyStoryModule = module {
    single<WeeklyRecapStream> {
        statsAndAchievementsRecapStream(
            observeStats = get(),
            observeAchievements = get(),
            activeCrew = get(),
            session = get(),
            mealRead = get(),
            clock = get(),
            zone = get(),
        )
    }
    viewModel { (source: DigestStorySource) ->
        WeeklyStoryViewModel(
            recapStream = get(),
            source = source,
            storyShareController = get(),
            clock = get(),
            zone = get(),
            analytics = get(),
        )
    }
}
