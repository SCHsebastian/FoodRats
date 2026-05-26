package es.schsebastian.foodrats.feature.notifications.presentation.permission

import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.core.presentation.mvi.MviEffect
import es.schsebastian.foodrats.core.presentation.mvi.MviIntent
import es.schsebastian.foodrats.core.presentation.mvi.MviState
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission

data class NotificationPermissionState(
    val current: NotificationPermission = NotificationPermission.NotYetRequested,
    val isRequesting: Boolean = false,
    // Set when persisting the gate decision (markPrompted / set) fails. The gate advances only once
    // the prompted flag is durably written, so on failure we surface this and let the user retry
    // rather than stranding them on a screen whose "Continue" silently did nothing.
    val error: StringKey? = null,
) : MviState

sealed interface NotificationPermissionIntent : MviIntent {
    data object Request : NotificationPermissionIntent
    data object Skip : NotificationPermissionIntent
    data object OpenSettings : NotificationPermissionIntent
}

sealed interface NotificationPermissionEffect : MviEffect {
    data object Continue : NotificationPermissionEffect
}
