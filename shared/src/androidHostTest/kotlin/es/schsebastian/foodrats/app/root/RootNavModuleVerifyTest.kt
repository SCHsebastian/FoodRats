package es.schsebastian.foodrats.app.root

import es.schsebastian.foodrats.app.connectivity.ConnectivityViewModel
import es.schsebastian.foodrats.app.consent.ConsentViewModel
import es.schsebastian.foodrats.app.di.appModules
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.ConsentPort
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.core.domain.crew.ActiveCrewProvider
import es.schsebastian.foodrats.core.domain.crew.CrewMembershipPort
import es.schsebastian.foodrats.core.domain.preferences.EulaPort
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.session.SessionProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Static Koin graph check for [rootNavModule] (via [appModules]).
 *
 * Regression guard for C1: [RootNavViewModel] must receive the real [EulaPort] (i.e.
 * `EulaRepository`) from the Koin graph. If the binding reverts to `viewModelOf`, Koin would
 * silently use the constructor default (`NoopEulaAcceptance`) and the EULA gate would never fire.
 * This test fails at the binding-verification step if [EulaPort] is removed from [extraTypes]
 * (meaning it is no longer declared as a dependency of `RootNavViewModel` in the module).
 *
 * [extraTypes] are the cross-module singletons that [rootNavModule] consumes but does not bind;
 * they are wired by [coreDataModule] / platform modules in the full [appModules] list.
 */
class RootNavModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun root_nav_module_graph_is_complete_and_eula_port_is_wired() {
        // Verify the three bindings introduced by rootNavModule (RootNavViewModel,
        // ConnectivityViewModel, ConsentViewModel). extraTypes lists everything they consume
        // that is bound outside this module.
        appModules.first().verify(
            extraTypes = listOf(
                // RootNavViewModel deps
                SessionProvider::class,
                ActiveCrewProvider::class,
                NotificationsPreferencePort::class,
                ConsentPort::class,
                EulaPort::class,
                CrewMembershipPort::class,
                // ConnectivityViewModel dep
                ConnectivityPort::class,
                // ConsentViewModel dep (analytics is explicit in the module binding)
                AnalyticsPort::class,
                // shared infra
                DispatcherProvider::class,
            ),
        )
    }
}
