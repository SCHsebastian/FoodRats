package es.schsebastian.foodrats.feature.crew.presentation.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey

@Composable
fun DeleteCrewConfirmDialog(
    crewName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(resolve(CrewStringKey.SettingsDeleteConfirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(resolve(CrewStringKey.SettingsCancel))
            }
        },
        title = { Text(resolve(CrewStringKey.SettingsDeleteTitle)) },
        text = { Text(resolve(CrewStringKey.SettingsDeleteBody, crewName)) },
    )
}
