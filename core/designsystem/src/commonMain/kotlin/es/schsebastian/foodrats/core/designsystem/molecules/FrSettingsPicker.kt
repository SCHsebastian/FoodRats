package es.schsebastian.foodrats.core.designsystem.molecules

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassRadio
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassSheet
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Bottom-sheet radio picker. Pass the localized [title], a list of option
 * `(id, label)` pairs, the currently-selected id, and `onSelect` which both
 * commits the choice and dismisses the sheet.
 *
 * No domain types — caller maps from its presentation model.
 *
 * Structural look: the Material [ModalBottomSheet] is kept for the scrim + tap-outside dismiss +
 * drag gesture, but stripped to transparent so an [FrGlassSheet] frosted stratum renders the body.
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
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
    ) {
        FrGlassSheet(
            modifier = Modifier.fillMaxWidth(),
            showGrabHandle = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Cap the height and scroll: short lists (theme/language) wrap naturally; long ones
                    // (the 24-hour reminder picker) stay reachable instead of overflowing off-screen.
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrText(
                    text = title,
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                    modifier = Modifier.semantics { heading() },
                )
                options.forEach { (id, label) ->
                    Row(
                        // One labelled radio node: the row owns selection (role + the merged label) and
                        // a min 48dp target; the inner FrGlassRadio is purely decorative (onClick = null,
                        // so it adds NO semantics node) so TalkBack announces "<label>, radio button,
                        // selected" instead of a second, bare, unlabelled control (WCAG 4.1.2 / 2.5.5).
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .selectable(
                                selected = id == selectedId,
                                role = Role.RadioButton,
                                onClick = { onSelect(id) },
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FrGlassRadio(
                            selected = id == selectedId,
                            onClick = null,
                        )
                        FrText(
                            text = label,
                            style = StructuralType.body,
                            color = StructuralColors.foreground,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}
