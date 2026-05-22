package es.schsebastian.foodrats.feature.notifications.presentation.permission

import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeGateway(
    var currentValue: NotificationPermission = NotificationPermission.NotYetRequested,
    var requestResult: NotificationPermission = NotificationPermission.Granted,
) : NotificationPermissionGateway {
    var settingsOpened = false
    override suspend fun current() = currentValue
    override suspend fun request() = requestResult
    override fun openSystemSettings() { settingsOpened = true }
}

class FakePrefs : NotificationsPreferencePort {
    private val _enabled = MutableStateFlow(true)
    private val _prompted = MutableStateFlow(false)
    override val enabled = _enabled.asStateFlow()
    override val prompted = _prompted.asStateFlow()
    var setCalledWith: Boolean? = null
    var markPromptedCalled = false
    override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> {
        setCalledWith = enabled
        _enabled.value = enabled
        return Result.success(Unit)
    }
    override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> {
        markPromptedCalled = true
        _prompted.value = true
        return Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPermissionViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun request_granted_marks_prompted_and_sets_pref_true() = runTest {
        val g = FakeGateway(requestResult = NotificationPermission.Granted)
        val p = FakePrefs()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.Request)
        assertEquals(NotificationPermission.Granted, vm.state.value.current)
        assertEquals(true, p.setCalledWith)
        assertTrue(p.markPromptedCalled)
    }

    @Test fun request_denied_marks_prompted_and_sets_pref_false() = runTest {
        val g = FakeGateway(requestResult = NotificationPermission.Denied)
        val p = FakePrefs()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.Request)
        assertEquals(NotificationPermission.Denied, vm.state.value.current)
        assertEquals(false, p.setCalledWith)
        assertTrue(p.markPromptedCalled)
    }

    @Test fun skip_marks_prompted_without_touching_enabled_pref() = runTest {
        val g = FakeGateway()
        val p = FakePrefs()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.Skip)
        assertTrue(p.markPromptedCalled)
        assertEquals(null, p.setCalledWith)
    }

    @Test fun open_settings_calls_gateway_and_marks_prompted() = runTest {
        val g = FakeGateway()
        val p = FakePrefs()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.OpenSettings)
        assertEquals(true, g.settingsOpened)
        assertTrue(p.markPromptedCalled)
    }

    @Test fun init_auto_marks_when_os_already_decided() = runTest {
        val g = FakeGateway(currentValue = NotificationPermission.Granted)
        val p = FakePrefs()
        NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        assertTrue(p.markPromptedCalled)
    }
}
