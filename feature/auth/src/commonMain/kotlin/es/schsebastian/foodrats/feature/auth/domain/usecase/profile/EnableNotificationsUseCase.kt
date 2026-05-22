package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.domain.error.toProfileError

/**
 * Enable notifications from the settings screen: requests the OS permission if needed,
 * then persists the user preference. Returns a typed failure when the OS denies — the
 * UI uses [ProfileError.Notifications.PermissionDeniedForever] to surface an
 * "Open settings" CTA.
 */
class EnableNotificationsUseCase(
    private val gate: NotificationPermissionPort,
    private val prefs: NotificationsPreferencePort,
) {
    suspend operator fun invoke(): Result<Unit, ProfileError> {
        val current = gate.current()
        val outcome = if (current == NotificationPermissionStatus.Granted) current else gate.request()
        return when (outcome) {
            NotificationPermissionStatus.Granted -> when (val r = prefs.set(true)) {
                is Result.Ok  -> Result.success(Unit)
                is Result.Err -> Result.failure(r.error.toProfileError())
            }
            NotificationPermissionStatus.Denied,
            NotificationPermissionStatus.NotYetRequested -> {
                // Keep the pref off so the UI stays in sync with the OS state.
                prefs.set(false)
                Result.failure(ProfileError.Notifications.PermissionDenied)
            }
            NotificationPermissionStatus.DeniedForever -> {
                prefs.set(false)
                Result.failure(ProfileError.Notifications.PermissionDeniedForever)
            }
        }
    }
}
