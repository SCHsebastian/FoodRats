package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

/**
 * One-shot assistive chip ("Edit", "Share", "Try again"). Always has `Role.Button` semantics.
 *
 * For *selection* state — multi-select tags, single-select scores — use [FrFilterChip] instead.
 * Mixing assist with selection ends up faking selection through color and loses the
 * `Role.Checkbox` / `Role.RadioButton` announcements TalkBack/VoiceOver rely on.
 */
@Composable
fun FrChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        label = { FrText(text = label) },
        modifier = modifier,
        enabled = enabled,
    )
}

@FrPreview
@Composable
private fun FrChipPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrChip(label = "Edit",     onClick = {})
            FrChip(label = "Try again", onClick = {})
            FrChip(label = "Off",      onClick = {}, enabled = false)
        }
    }
}
