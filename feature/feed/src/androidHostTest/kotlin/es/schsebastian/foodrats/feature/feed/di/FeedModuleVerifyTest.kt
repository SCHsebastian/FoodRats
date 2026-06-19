package es.schsebastian.foodrats.feature.feed.di

import es.schsebastian.foodrats.core.data.share.StoryShareController
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewBlindVotingPort
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort
import es.schsebastian.foodrats.core.domain.meal.IngredientReadPort
import es.schsebastian.foodrats.core.domain.meal.MealCommentPort
import es.schsebastian.foodrats.core.domain.meal.MealDeletePort
import es.schsebastian.foodrats.core.domain.meal.MealRatingPort
import es.schsebastian.foodrats.core.domain.meal.MealReactionPort
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.meal.MealUploadProgressPort
import es.schsebastian.foodrats.core.domain.meal.QueuedUploadActionsPort
import es.schsebastian.foodrats.core.domain.outbox.OutboxPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [feedModule]: a missing or mis-typed binding fails here instead of at
 * app launch.
 *
 * [extraTypes] are the cross-module ports/types feed consumes but does not bind — the read/rate/
 * delete/comment ports owned by `:feature:meal`, plus `:core:domain` ports and `:core:data`'s
 * `TimeZone`, all wired in the `shared` aggregator. `verify` is JVM-only, so this lives in
 * androidHostTest.
 */
class FeedModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun feed_module_graph_is_complete() {
        feedModule.verify(
            extraTypes = listOf(
                ActiveCrewProvider::class,
                CrewMembershipPort::class,
                CrewBlindVotingPort::class,
                SessionProvider::class,
                AccountReadPort::class,
                CrewOwnerPort::class,
                MealReadPort::class,
                MealRatingPort::class,
                MealReactionPort::class,
                MealDeletePort::class,
                MealCommentPort::class,
                MealUploadProgressPort::class,
                QueuedUploadActionsPort::class,
                IngredientReadPort::class,
                Clock::class,
                TimeZone::class,
                StoryShareController::class,
                AnalyticsPort::class,
                ConnectivityPort::class,
                OutboxPort::class,
            ),
        )
    }
}
