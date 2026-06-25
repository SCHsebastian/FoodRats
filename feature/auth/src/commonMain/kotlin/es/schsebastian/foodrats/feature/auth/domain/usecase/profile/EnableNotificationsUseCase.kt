package es.schsebastian.foodrats.feature.auth.domain.usecase.profile

import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionPort
import es.schsebastian.foodrats.core.domain.notifications.NotificationPermissionStatus
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
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
                syncPrefOff()
                Result.failure(ProfileError.Notifications.PermissionDenied)
            }
            NotificationPermissionStatus.DeniedForever -> {
                syncPrefOff()
                Result.failure(ProfileError.Notifications.PermissionDeniedForever)
            }
        }
    }

    /**
     * Keep the stored opt-in off so the UI toggle stays in sync with the denied OS permission. The
     * permission error is the user-facing outcome we return, but a failed persist must NOT be
     * silently dropped (it would leave a stale "on" toggle) — log it so it's observable.
     */
    private suspend fun syncPrefOff() {
        if (prefs.set(false) is Result.Err) {
            FrLog.w("Notifications") { "EnableNotifications: failed to persist pref=off after OS denial" }
        }
    }
}
