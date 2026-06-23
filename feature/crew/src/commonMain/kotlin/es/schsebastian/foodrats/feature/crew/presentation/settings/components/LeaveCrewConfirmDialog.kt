package es.schsebastian.foodrats.feature.crew.presentation.settings.components

import androidx.compose.runtime.Composable
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

/**
 * Confirmation gate before a member leaves a crew (mirrors [DeleteCrewConfirmDialog]). Leaving is
 * reversible (rejoin with an invite code) but still a danger-zone action, so it renders destructive.
 */
@Composable
fun LeaveCrewConfirmDialog(
    crewName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FrConfirmDialog(
        title = resolve(CrewStringKey.SettingsLeaveTitle),
        message = resolve(CrewStringKey.SettingsLeaveBody, crewName),
        confirmLabel = resolve(CrewStringKey.SettingsLeaveConfirm),
        dismissLabel = resolve(CrewStringKey.SettingsCancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}
