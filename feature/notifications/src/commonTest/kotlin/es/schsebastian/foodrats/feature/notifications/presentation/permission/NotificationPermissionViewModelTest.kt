package es.schsebastian.foodrats.feature.notifications.presentation.permission

import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.repository.NotificationPermissionGateway
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
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
    var markPromptedResult: Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
    override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> {
        setCalledWith = enabled
        _enabled.value = enabled
        return Result.success(Unit)
    }
    override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> {
        markPromptedCalled = true
        if (markPromptedResult is Result.Ok) _prompted.value = true
        return markPromptedResult
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

    @Test fun skip_persist_failure_surfaces_retryable_error() = runTest {
        val g = FakeGateway()
        val p = FakePrefs().apply {
            markPromptedResult = Result.failure(NotificationsPreferenceError.Persist.Unavailable)
        }
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.Skip)
        // The prompted flag never flipped, so the gate must NOT silently continue — instead it
        // shows a retry-able error so the user isn't stranded.
        assertTrue(p.markPromptedCalled)
        assertEquals(NotificationStringKey.PermissionSaveFailed, vm.state.value.error)
        assertEquals(false, p.prompted.value)
    }

    @Test fun successful_skip_clears_error() = runTest {
        val g = FakeGateway()
        val p = FakePrefs()
        val vm = NotificationPermissionViewModel(RequestNotificationPermissionUseCase(g), p)
        vm.onIntent(NotificationPermissionIntent.Skip)
        assertEquals(null, vm.state.value.error)
        assertTrue(p.prompted.value)
    }
}
