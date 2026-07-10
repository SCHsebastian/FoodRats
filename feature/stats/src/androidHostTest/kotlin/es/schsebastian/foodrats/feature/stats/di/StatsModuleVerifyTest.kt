package es.schsebastian.foodrats.feature.stats.di

import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.domain.account.BlockedAccountsPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewWelcomePort
import es.schsebastian.foodrats.core.domain.cuisine.CuisineReadPort
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [statsModule]: every constructor of every binding the module declares
 * resolves to either a binding in this module or one of [extraTypes] — otherwise a real (mis-typed
 * or missing) binding fails here instead of crashing at app launch.
 *
 * [extraTypes] are the cross-module ports/types this feature consumes but does NOT itself bind. They
 * come from `:core:domain` and from `:feature:meal` (`MealReadPort`, `MealUploadProgressPort`),
 * wired together only in the `shared` aggregator. Declaring them here is the documented way to avoid
 * false-positives on cross-module bindings (`verify` is JVM-only, hence this is an androidHostTest).
 */
class StatsModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun stats_module_graph_is_complete() {
        statsModule.verify(
            extraTypes = listOf(
                ActiveCrewProvider::class,
                SessionProvider::class,
                MealReadPort::class,
                IngredientReadPort::class,
                CuisineReadPort::class,
                // UGC compliance §5 — stats excludes blocked authors from every ranking.
                BlockedAccountsPort::class,
                MealUploadProgressPort::class,
                Clock::class,
                TimeZone::class,
                StoryShareController::class,
                AnalyticsPort::class,
                // C8b — crew's score style, so leaderboard cards render Stars/Emoji/Numeric.
                CrewWelcomePort::class,
                // ObserveStatsUseCase's defaulted computeDispatcher (Dispatchers.Default) — the
                // off-main hop for the heavy stats compute; not a graph binding.
                CoroutineDispatcher::class,
            ),
        )
    }
}
