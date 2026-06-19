package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Bottom-sheet radio picker. Pass the localized [title], a list of option
 * `(id, label)` pairs, the currently-selected id, and `onSelect` which both
 * commits the choice and dismisses the sheet.
 *
 * No domain types — caller maps from its presentation model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrSettingsPicker(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Cap the height and scroll: short lists (theme/language) wrap naturally; long ones
                // (the 24-hour reminder picker) stay reachable instead of overflowing off-screen.
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            options.forEach { (id, label) ->
                Row(
                    // One labelled radio node: the row owns selection (role + the merged label) and
                    // a min 48dp target; the inner RadioButton is decorative (onClick = null) so
                    // TalkBack announces "<label>, radio button, selected" instead of a bare,
                    // unlabelled control (WCAG 4.1.2 / 2.5.5).
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.touchTarget)
                        .selectable(
                            selected = id == selectedId,
                            role = Role.RadioButton,
                            onClick = { onSelect(id) },
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = id == selectedId,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    FrText(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
}
