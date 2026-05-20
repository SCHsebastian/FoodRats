package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

/**
 * Selection chip. The canonical Material 3 primitive for chip groups where the user picks
 * one (single-select) or several (multi-select) items.
 *
 * Why this exists alongside [FrChip]: AssistChip carries `Role.Button` semantics, which means
 * TalkBack/VoiceOver won't announce a selected state. FilterChip carries the correct
 * `Role.Checkbox` semantics and renders a leading check on selected — fixed visual + a11y
 * affordance in one primitive.
 *
 * The 32dp visual height is auto-inflated to a 48dp tap target by M3 internally.
 */
@Composable
fun FrFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheckWhenSelected: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { FrText(text = label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = if (selected && showCheckWhenSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@FrPreview
@Composable
private fun FrFilterChipPreview() {
    FrPreviewLightDark {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FrFilterChip(label = "Pizza", selected = true, onClick = {})
                FrFilterChip(label = "Salad", selected = false, onClick = {})
                FrFilterChip(label = "Off",   selected = false, onClick = {}, enabled = false)
            }
            FrFilterChip(label = "No check on select", selected = true, onClick = {}, showCheckWhenSelected = false)
        }
    }
}
