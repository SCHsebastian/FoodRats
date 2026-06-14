package es.schsebastian.foodrats.feature.notifications.presentation.permission

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsEvent
import es.schsebastian.foodrats.core.domain.analytics.AnalyticsPort
import es.schsebastian.foodrats.core.domain.analytics.NoopAnalyticsTracker
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.domain.result.Result
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import kotlinx.coroutines.launch

class NotificationPermissionViewModel(
    private val uc: RequestNotificationPermissionUseCase,
    private val prefs: NotificationsPreferencePort,
    private val analytics: AnalyticsPort = NoopAnalyticsTracker,
) : MviViewModel<NotificationPermissionState, NotificationPermissionIntent, NotificationPermissionEffect>(
    NotificationPermissionState(),
) {
    init {
        viewModelScope.launch {
            val current = uc.current()
            update { it.copy(current = current) }
            // If the OS already has a decision for this account (e.g. previous install
            // granted it, or the user denied it via Settings before signing in here),
            // there's nothing to ask — complete the gate and continue. If the write fails
            // we leave the screen up; its buttons let the user retry.
            if (current != NotificationPermission.NotYetRequested && markPrompted()) {
                emit(NotificationPermissionEffect.Continue)
            }
        }
    }

    override suspend fun handle(intent: NotificationPermissionIntent) {
        when (intent) {
            NotificationPermissionIntent.Request -> {
                analytics.track(AnalyticsEvent.NotifPermissionPrompted(promptCount = 1))
                update { it.copy(isRequesting = true, error = null) }
                val r = uc()
                update { it.copy(isRequesting = false, current = r) }
                analytics.track(
                    if (r == NotificationPermission.Granted) AnalyticsEvent.NotifPermissionGranted
                    else AnalyticsEvent.NotifPermissionDenied,
                )
                // Sync the user preference with the OS outcome (best-effort: a failure here
                // doesn't strand the user, it only leaves the toggle at its default).
                prefs.set(enabled = r == NotificationPermission.Granted)
                advanceOrShowError()
            }
            NotificationPermissionIntent.Skip -> {
                // User deferred; mark the gate complete but leave the preference at its
                // default so they can opt in later from settings without rerunning the
                // onboarding screen.
                update { it.copy(error = null) }
                advanceOrShowError()
            }
            NotificationPermissionIntent.OpenSettings -> {
                // Marking prompted here too — if the user is in DeniedForever, sending
                // them to Settings is the only way forward; they shouldn't see this gate
                // again on next signin. Surface a save failure but still open Settings.
                if (!markPrompted()) update { it.copy(error = NotificationStringKey.PermissionSaveFailed) }
                uc.openSettings()
            }
        }
    }

    /**
     * Persist the gate-complete flag and either continue (the [NotificationsPreferencePort.prompted]
     * flip is what actually drives `RootNavViewModel` past this screen) or surface a retry-able error.
     * Navigation is gated on the *durable* write, so a failed write must not silently no-op.
     */
    private suspend fun advanceOrShowError() {
        if (markPrompted()) emit(NotificationPermissionEffect.Continue)
        else update { it.copy(error = NotificationStringKey.PermissionSaveFailed) }
    }

    private suspend fun markPrompted(): Boolean = prefs.markPrompted() is Result.Ok
}
