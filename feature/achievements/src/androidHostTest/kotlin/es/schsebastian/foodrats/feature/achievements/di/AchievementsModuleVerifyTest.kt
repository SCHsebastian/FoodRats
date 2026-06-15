package es.schsebastian.foodrats.feature.achievements.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.meal.MealReadPort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import es.schsebastian.foodrats.core.domain.time.Clock
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [achievementsModule]: every constructor of every binding the module
 * declares resolves to either a binding in this module or one of [extraTypes] — otherwise a real
 * (mis-typed or missing) binding fails here instead of crashing at app launch (`verify` is
 * JVM-only, hence this is an androidHostTest).
 *
 * [extraTypes] are the cross-module types this feature's data-layer bindings consume but do NOT
 * themselves bind — both provided by `coreDataModule` in the `shared` aggregator. The presentation
 * task adds `MealReadPort`/`ActiveCrewProvider`/`SessionProvider`/`Clock`/`AnalyticsPort` here when
 * it adds the use case + viewModel.
 */
class AchievementsModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun achievements_module_graph_is_complete() {
        achievementsModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                DispatcherProvider::class,
                MealReadPort::class,
                ActiveCrewProvider::class,
                SessionProvider::class,
                Clock::class,
                TimeZone::class,
                AnalyticsPort::class,
            ),
        )
    }
}
