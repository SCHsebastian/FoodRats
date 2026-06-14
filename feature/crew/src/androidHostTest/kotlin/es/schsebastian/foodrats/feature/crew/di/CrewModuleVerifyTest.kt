package es.schsebastian.foodrats.feature.crew.di

import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.schsebastian.foodrats.core.domain.account.AccountReadPort
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.random.Random

/**
 * Static Koin graph check for [crewModule]: a missing or mis-typed binding fails here instead of at
 * app launch.
 *
 * [extraTypes]:
 *  - `FirebaseFirestore`, `DispatcherProvider`, `SessionProvider`, `AccountReadPort` are app-wide /
 *    `:feature:auth`-owned dependencies wired in the `shared` aggregator.
 *  - `Random` backs `CrewCodeGenerator` (passed as `Random.Default` in the binding lambda; the ctor
 *    is still reflected).
 *  - `CrewId` is the runtime parameter of `CrewSettingsViewModel` (`viewModel { (crewId) -> ... }`);
 *    `verify` reflects the VM constructor including that parameter, so the type must be declared.
 *
 * `verify` is JVM-only, so this lives in androidHostTest.
 */
class CrewModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun crew_module_graph_is_complete() {
        crewModule.verify(
            extraTypes = listOf(
                FirebaseFirestore::class,
                DispatcherProvider::class,
                SessionProvider::class,
                AccountReadPort::class,
                Random::class,
                CrewId::class,
                AnalyticsPort::class,
            ),
        )
    }
}
