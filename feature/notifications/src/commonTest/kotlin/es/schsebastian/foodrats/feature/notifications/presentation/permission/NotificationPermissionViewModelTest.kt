package es.schsebastian.foodrats.feature.notifications.presentation.permission

import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeGateway(
    var currentValue: NotificationPermission = NotificationPermission.NotYetRequested,
    var requestResult: NotificationPermission = NotificationPermission.Granted,
) : NotificationPermissionGateway {
    var settingsOpened = false
    override suspend fun current() = currentValue
    override suspend fun request() = requestResult
    override fun openSystemSettings() { settingsOpened = true }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun request_granted_emits_continue() = runTest {
        val g = FakeGateway(requestResult = NotificationPermission.Granted)
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g))
        vm.onIntent(NotificationPermissionIntent.Request)
        assertEquals(NotificationPermission.Granted, vm.state.value.current)
    }

    @Test fun request_denied_stays_on_screen() = runTest {
        val g = FakeGateway(requestResult = NotificationPermission.Denied)
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g))
        vm.onIntent(NotificationPermissionIntent.Request)
        assertEquals(NotificationPermission.Denied, vm.state.value.current)
    }

    @Test fun open_settings_calls_gateway() = runTest {
        val g = FakeGateway()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g))
        vm.onIntent(NotificationPermissionIntent.OpenSettings)
        assertEquals(true, g.settingsOpened)
    }
}
