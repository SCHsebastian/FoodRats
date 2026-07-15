package es.schsebastian.foodrats.app.connectivity

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.connectivity.ConnectivityPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Test double: drives the online flag [ConnectivityViewModel] projects into [ConnectivityViewModel.isOnline]. */
private class FakeConnectivityPort(online: Boolean = true) : ConnectivityPort {
    val flow = MutableStateFlow(online)
    override fun isOnline(): Flow<Boolean> = flow
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun subscribing_while_port_reports_online_shows_no_banner() = runTest {
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun offline_emission_from_port_shows_the_banner_state() = runTest {
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun going_back_online_hides_the_banner_again() = runTest {
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            assertEquals(false, awaitItem())

            connectivity.flow.value = true
            assertEquals(true, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun subscribing_while_port_already_reports_offline_surfaces_offline_immediately() = runTest {
        // The port emits its current value on subscribe (contract in ConnectivityPort's KDoc), and
        // the projection's stateIn(initialValue = true) collapses under UnconfinedTestDispatcher
        // before the first collector observes anything — so a subscriber that arrives after the
        // port already went offline sees `false`, not a stale `true`.
        val connectivity = FakeConnectivityPort(online = false)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rapid_flapping_conflates_to_distinct_state_changes_only() = runTest {
        // StateFlow conflates: repeating the same value is not a new emission. Turbine's
        // expectMostRecentItem() (not awaitItem()) reads the settled value after a burst,
        // matching this module's convention for coalesced/transient intermediates.
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            connectivity.flow.value = true
            connectivity.flow.value = true // repeat: StateFlow must not re-emit this
            connectivity.flow.value = false
            connectivity.flow.value = false // repeat: StateFlow must not re-emit this
            connectivity.flow.value = true

            assertEquals(true, expectMostRecentItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun each_distinct_flap_is_observable_in_order() = runTest {
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            assertEquals(false, awaitItem())

            connectivity.flow.value = true
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            assertEquals(false, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun independent_collectors_each_see_the_current_projected_value() = runTest {
        // WhileSubscribed(5_000) restarts/keeps the upstream collection alive per active
        // subscriber; a second, independently-started collector must observe the same
        // conflated state as the first rather than a stale or re-seeded value.
        val connectivity = FakeConnectivityPort(online = true)
        val vm = ConnectivityViewModel(connectivity)

        vm.isOnline.test {
            assertEquals(true, awaitItem())

            connectivity.flow.value = false
            assertEquals(false, awaitItem())

            vm.isOnline.test {
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }
}
