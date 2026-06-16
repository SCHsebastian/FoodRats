package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Confirmation dialog with a primary destructive/affirmative action and an
 * optional secondary dismiss action.
 *
 * No domain types — pass primitive strings (resolve `StringKey`s at the call
 * site). Designed for "are you sure?" gates before irreversible flows
 * (publish a meal, leave a crew, sign out). Set [destructive] for irreversible
 * actions (delete account, delete meal, remove member) — the confirm action then
 * renders in the `danger` semantic color.
 *
 * Leave [dismissLabel] `null`/blank for a **single-action acknowledge** dialog
 * (e.g. "Badge unlocked! → Nice!"): only the confirm button renders. [onDismiss]
 * still backs the back-press / scrim tap regardless.
 */
@Composable
fun FrConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            FrText(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = message?.let { msg ->
            {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrText(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                FrText(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) LocalFrSemanticColors.current.danger else Color.Unspecified,
                )
            }
        },
        dismissButton = dismissLabel?.takeIf { it.isNotBlank() }?.let { label ->
            {
                TextButton(onClick = onDismiss) {
                    FrText(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    )
}
