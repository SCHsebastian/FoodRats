package es.schsebastian.foodrats.feature.notifications.presentation.permission

import androidx.lifecycle.viewModelScope
import es.schsebastian.foodrats.core.presentation.mvi.MviViewModel
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.domain.usecase.RequestNotificationPermissionUseCase
import kotlinx.coroutines.launch

class NotificationPermissionViewModel(
    private val uc: RequestNotificationPermissionUseCase,
) : MviViewModel<NotificationPermissionState, NotificationPermissionIntent, NotificationPermissionEffect>(
    NotificationPermissionState(),
) {
    init {
        viewModelScope.launch {
            val current = uc.current()
            update { it.copy(current = current) }
        }
    }

    override suspend fun handle(intent: NotificationPermissionIntent) {
        when (intent) {
            NotificationPermissionIntent.Request -> {
                update { it.copy(isRequesting = true) }
                val r = uc()
                update { it.copy(isRequesting = false, current = r) }
                if (r == NotificationPermission.Granted) emit(NotificationPermissionEffect.Continue)
            }
            NotificationPermissionIntent.Skip         -> emit(NotificationPermissionEffect.Continue)
            NotificationPermissionIntent.OpenSettings -> uc.openSettings()
        }
    }
}
