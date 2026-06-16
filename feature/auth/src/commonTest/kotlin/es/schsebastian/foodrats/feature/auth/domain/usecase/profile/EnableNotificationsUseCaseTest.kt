package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferenceError
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnableNotificationsUseCaseTest {

    private class FakeNotificationPermissionPort(
        private val onCurrent: NotificationPermissionStatus,
        private val onRequest: NotificationPermissionStatus = onCurrent,
    ) : NotificationPermissionPort {
        var openSettingsCount = 0
            private set
        override suspend fun current() = onCurrent
        override suspend fun request() = onRequest
        override fun openSystemSettings() {
            openSettingsCount++
        }
    }

    private class FakeNotificationsPreferencePort(
        private val setResult: Result<Unit, NotificationsPreferenceError> = Result.success(Unit),
    ) : NotificationsPreferencePort {
        val setCalls: MutableList<Boolean> = mutableListOf()
        override val enabled: Flow<Boolean> = flowOf(false)
        override val prompted: Flow<Boolean> = flowOf(false)
        override suspend fun set(enabled: Boolean): Result<Unit, NotificationsPreferenceError> {
            setCalls += enabled
            return setResult
        }
        override suspend fun markPrompted(): Result<Unit, NotificationsPreferenceError> = Result.success(Unit)
    }

    @Test
    fun granted_persists_true_and_succeeds() = runTest {
        val gate = FakeNotificationPermissionPort(NotificationPermissionStatus.Granted)
        val prefs = FakeNotificationsPreferencePort()

        val r = EnableNotificationsUseCase(gate, prefs).invoke()

        assertTrue(r is Result.Ok)
        assertEquals(listOf(true), prefs.setCalls)
    }

    @Test
    fun granted_but_persist_fails_propagates_profile_error() = runTest {
        val gate = FakeNotificationPermissionPort(NotificationPermissionStatus.Granted)
        val prefs = FakeNotificationsPreferencePort(
            setResult = Result.failure(NotificationsPreferenceError.Persist.Unavailable),
        )

        val r = EnableNotificationsUseCase(gate, prefs).invoke()

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Notifications.PersistFailed, r.error)
        assertEquals(listOf(true), prefs.setCalls)
    }

    @Test
    fun soft_deny_persists_false_and_returns_permission_denied() = runTest {
        val gate = FakeNotificationPermissionPort(
            onCurrent = NotificationPermissionStatus.NotYetRequested,
            onRequest = NotificationPermissionStatus.Denied,
        )
        val prefs = FakeNotificationsPreferencePort()

        val r = EnableNotificationsUseCase(gate, prefs).invoke()

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Notifications.PermissionDenied, r.error)
        assertEquals(listOf(false), prefs.setCalls)
    }

    @Test
    fun not_yet_requested_outcome_persists_false_and_returns_permission_denied() = runTest {
        val gate = FakeNotificationPermissionPort(
            onCurrent = NotificationPermissionStatus.NotYetRequested,
            onRequest = NotificationPermissionStatus.NotYetRequested,
        )
        val prefs = FakeNotificationsPreferencePort()

        val r = EnableNotificationsUseCase(gate, prefs).invoke()

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Notifications.PermissionDenied, r.error)
        assertEquals(listOf(false), prefs.setCalls)
    }

    @Test
    fun hard_deny_persists_false_and_returns_permission_denied_forever() = runTest {
        val gate = FakeNotificationPermissionPort(
            onCurrent = NotificationPermissionStatus.NotYetRequested,
            onRequest = NotificationPermissionStatus.DeniedForever,
        )
        val prefs = FakeNotificationsPreferencePort()

        val r = EnableNotificationsUseCase(gate, prefs).invoke()

        assertTrue(r is Result.Err)
        assertEquals(ProfileError.Notifications.PermissionDeniedForever, r.error)
        assertEquals(listOf(false), prefs.setCalls)
    }
}
