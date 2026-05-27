package es.schsebastian.foodrats.feature.crew.presentation.settings.components

import androidx.compose.runtime.Composable
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

@Composable
fun DeleteCrewConfirmDialog(
    crewName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FrConfirmDialog(
        title = resolve(CrewStringKey.SettingsDeleteTitle),
        message = resolve(CrewStringKey.SettingsDeleteBody, crewName),
        confirmLabel = resolve(CrewStringKey.SettingsDeleteConfirm),
        dismissLabel = resolve(CrewStringKey.SettingsCancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true,
    )
}
