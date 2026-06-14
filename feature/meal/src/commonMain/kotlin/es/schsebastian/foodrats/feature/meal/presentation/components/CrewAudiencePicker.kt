package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrFilterChip
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.model.CrewId

/**
 * Publish-audience picker: choose which crews a plate is shared with.
 *
 * An "All" shortcut chip selects every crew; the per-crew chips multi-select a subset —
 * which together cover the three modes (all / only this one / a custom list). The
 * ViewModel keeps at least one crew selected, so a tap that would clear the last chip is
 * ignored. Domain-aware (takes [CrewSummary]), so it lives here rather than in
 * `:core:designsystem`; it renders with the `FrFilterChip` atom (Role.Checkbox a11y).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrewAudiencePicker(
    title: String,
    allLabel: String,
    crews: List<CrewSummary>,
    selectedCrewIds: Set<CrewId>,
    onAllClick: () -> Unit,
    onCrewClick: (CrewId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FrText(text = title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        ) {
            FrFilterChip(
                label = allLabel,
                selected = crews.isNotEmpty() && selectedCrewIds.size == crews.size,
                onClick = onAllClick,
            )
            crews.forEach { crew ->
                FrFilterChip(
                    label = crew.name,
                    selected = crew.id in selectedCrewIds,
                    onClick = { onCrewClick(crew.id) },
                )
            }
        }
    }
}
