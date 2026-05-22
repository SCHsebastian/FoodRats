package es.schsebastian.foodrats.feature.notifications.presentation.permission

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.domain.preferences.NotificationsPreferencePort
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import kotlinx.coroutines.launch

class NotificationPermissionViewModel(
    private val uc: RequestNotificationPermissionUseCase,
    private val prefs: NotificationsPreferencePort,
) : MviViewModel<NotificationPermissionState, NotificationPermissionIntent, NotificationPermissionEffect>(
    NotificationPermissionState(),
) {
    init {
        viewModelScope.launch {
            val current = uc.current()
            update { it.copy(current = current) }
            // If the OS already has a decision for this account (e.g. previous install
            // granted it, or the user denied it via Settings before signing in here),
            // there's nothing to ask — mark the gate complete and continue so the user
            // doesn't get stuck on this screen.
            if (current != NotificationPermission.NotYetRequested) {
                prefs.markPrompted()
                emit(NotificationPermissionEffect.Continue)
            }
        }
    }

    override suspend fun handle(intent: NotificationPermissionIntent) {
        when (intent) {
            NotificationPermissionIntent.Request -> {
                update { it.copy(isRequesting = true) }
                val r = uc()
                update { it.copy(isRequesting = false, current = r) }
                // Sync the user preference with the OS outcome: an "Allow → Granted" means
                // the user wants notifications; "Allow → Denied/DeniedForever" means they
                // declined inside the OS dialog. Either way, the gate is now resolved.
                prefs.set(enabled = r == NotificationPermission.Granted)
                prefs.markPrompted()
                emit(NotificationPermissionEffect.Continue)
            }
            NotificationPermissionIntent.Skip         -> {
                // User deferred; mark the gate complete but leave the preference at its
                // default so they can opt in later from settings without rerunning the
                // onboarding screen.
                prefs.markPrompted()
                emit(NotificationPermissionEffect.Continue)
            }
            NotificationPermissionIntent.OpenSettings -> {
                // Marking prompted here too — if the user is in DeniedForever, sending
                // them to Settings is the only way forward; they shouldn't see this gate
                // again on next signin.
                prefs.markPrompted()
                uc.openSettings()
            }
        }
    }
}
