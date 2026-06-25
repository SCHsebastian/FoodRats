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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassRadio
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassSheet
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * The fixed set of report reasons the sheet offers. A *presentation* enum (NOT a domain type), so the
 * molecule stays free of `:core:domain` — the owning feature maps each option to/from its
 * `ReportReason` and supplies the already-resolved label for each. Ordered to mirror the report
 * domain's reason taxonomy (UGC compliance §4).
 */
enum class FrReportReasonOption { SPAM, HARASSMENT, HATE, SEXUAL, VIOLENCE, OTHER }

/**
 * Bottom-sheet reason picker for reporting a meal / comment / user (UGC compliance §4.4).
 *
 * Pure molecule: every string is already resolved by the caller, the reasons are a presentation
 * [FrReportReasonOption] (no domain types), and the only outputs are the [onSubmit]/[onDismiss]
 * callbacks. A single radio reason must be selected before [onSubmit] is enabled; [submitting]
 * disables both actions while the report is in flight.
 *
 * Structural look: the Material [ModalBottomSheet] is kept for the scrim + tap-outside dismiss +
 * drag gesture, but stripped to transparent so an [FrGlassSheet] frosted stratum renders the body.
 *
 * @param reasonLabels the label for each option to render, in iteration order. Options absent from
 *   the map are not shown (lets a caller offer a subset).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrReportSheet(
    title: String,
    reasonLabels: Map<FrReportReasonOption, String>,
    submitLabel: String,
    cancelLabel: String,
    submitting: Boolean,
    onSubmit: (FrReportReasonOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<FrReportReasonOption?>(null) }
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = { if (!submitting) onDismiss() },
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrText(
                    text = title,
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                    modifier = Modifier.semantics { heading() },
                )
                reasonLabels.forEach { (option, label) ->
                    Row(
                        // One labelled radio node: the row owns selection (role + merged label) and a
                        // 48dp min target; the inner FrGlassRadio is purely decorative (onClick = null,
                        // so it adds NO semantics node) so TalkBack announces "<label>, radio button,
                        // selected" — mirrors FrSettingsPicker.
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .selectable(
                                selected = option == selected,
                                enabled = !submitting,
                                role = Role.RadioButton,
                                onClick = { selected = option },
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FrGlassRadio(
                            selected = option == selected,
                            onClick = null,
                            enabled = !submitting,
                        )
                        FrText(
                            text = label,
                            style = StructuralType.body,
                            color = StructuralColors.foreground,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
                FrGlassButton(
                    label = submitLabel,
                    onClick = { selected?.let(onSubmit) },
                    tone = FrButtonTone.Danger,
                    enabled = selected != null && !submitting,
                    fillWidth = true,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                FrGlassButton(
                    label = cancelLabel,
                    onClick = onDismiss,
                    tone = FrButtonTone.Ghost,
                    enabled = !submitting,
                    fillWidth = true,
                )
            }
        }
    }
}
