package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import es.schsebastian.foodrats.core.domain.time.Clock
import es.schsebastian.foodrats.feature.achievements.domain.usecase.ObserveAchievementsUseCase
import es.schsebastian.foodrats.feature.stats.domain.usecase.ObserveStatsUseCase
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [weeklyStoryModule]: every constructor of every binding resolves to a
 * binding in this module or one of [extraTypes]. [extraTypes] are the cross-module dependencies the
 * recap player consumes but does NOT bind — the two feature use cases (`:feature:stats` /
 * `:feature:achievements`), `Clock`/`TimeZone` (`coreDataModule`), and the `AnalyticsPort` passed
 * explicitly (CHARTER §9). [DigestStorySource] is the viewModel's runtime parameter, supplied via
 * `injections` so `verify` resolves the parameterized binding.
 */
class WeeklyStoryModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun weekly_story_module_graph_is_complete() {
        weeklyStoryModule.verify(
            extraTypes = listOf(
                ObserveStatsUseCase::class,
                ObserveAchievementsUseCase::class,
                Clock::class,
                TimeZone::class,
                StoryShareController::class,
                AnalyticsPort::class,
                DigestStorySource::class,
            ),
            injections = injectedParameters(
                definition<WeeklyStoryViewModel>(DigestStorySource::class),
            ),
        )
    }
}
