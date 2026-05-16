package es.schsebastian.foodrats.feature.notifications.presentation.permission

import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission

data class NotificationPermissionState(
    val current: NotificationPermission = NotificationPermission.NotYetRequested,
    val isRequesting: Boolean = false,
) : MviState

sealed interface NotificationPermissionIntent : MviIntent {
    data object Request : NotificationPermissionIntent
    data object Skip : NotificationPermissionIntent
    data object OpenSettings : NotificationPermissionIntent
}

sealed interface NotificationPermissionEffect : MviEffect {
    data object Continue : NotificationPermissionEffect
}
