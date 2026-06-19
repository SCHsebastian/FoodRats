package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

/**
 * The FoodRats toggle switch. Thin wrapper over Material3 [Switch] pinned to the
 * brand colors (olive `primary` track when on) so feature code never reaches for
 * the raw Material component or hand-tunes `SwitchDefaults`.
 *
 * [contentDescription] names what the switch controls (WCAG 4.1.2 / 2.5.3). Material already
 * exposes the Switch role + on/off state to TalkBack; without a name it announces only
 * "on, switch" with no subject. Pass the row's label here when the switch is the only
 * actionable node in its row (e.g. a settings toggle).
 */
@Composable
fun FrSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else Modifier,
        ),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@FrPreview
@Composable
private fun FrSwitchPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FrSwitch(checked = true, onCheckedChange = {})
            FrSwitch(checked = false, onCheckedChange = {})
            FrSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
