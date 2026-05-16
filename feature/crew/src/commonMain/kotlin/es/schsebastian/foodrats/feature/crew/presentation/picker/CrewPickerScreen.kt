package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CrewPickerScreen(
    onCrewSelected: (crewId: String) -> Unit,
    vm: CrewPickerViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                is CrewPickerEffect.CrewSelected -> onCrewSelected(eff.crewId.value)
            }
        }
    }
    FrScreenScaffold {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg)) {
            FrText(text = resolve(CrewStringKey.PickerTitle))
            if (state.crews.isEmpty() && !state.showCreateForm && !state.showJoinForm) {
                FrEmptyState(
                    icon = FrIcons.Settings,
                    headline = resolve(CrewStringKey.PickerEmptyHeadline),
                    subtext = resolve(CrewStringKey.PickerEmptySubtext),
                    cta = {},
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.crews, key = { it.id.value }) { crew ->
                        FrButton(
                            label = "${crew.name} (${crew.size}/8)",
                            onClick = { vm.onIntent(CrewPickerIntent.PickCrew(crew.id)) },
                            variant = FrButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrButton(
                    label = resolve(CrewStringKey.PickerCreateCta),
                    onClick = { vm.onIntent(CrewPickerIntent.ToggleCreateForm) },
                    variant = FrButtonVariant.Primary,
                )
                FrButton(
                    label = resolve(CrewStringKey.PickerJoinCta),
                    onClick = { vm.onIntent(CrewPickerIntent.ToggleJoinForm) },
                    variant = FrButtonVariant.Secondary,
                )
            }

            if (state.showCreateForm) {
                FrTextField(
                    value = state.createInput,
                    onValueChange = { vm.onIntent(CrewPickerIntent.CreateInputChanged(it)) },
                    label = resolve(CrewStringKey.CreateNameLabel),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
                FrButton(
                    label = resolve(CrewStringKey.CreateSubmit),
                    onClick = { vm.onIntent(CrewPickerIntent.SubmitCreate) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            if (state.showJoinForm) {
                FrTextField(
                    value = state.joinInput,
                    onValueChange = { vm.onIntent(CrewPickerIntent.JoinInputChanged(it)) },
                    label = resolve(CrewStringKey.JoinCodeLabel),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
                FrButton(
                    label = resolve(CrewStringKey.JoinSubmit),
                    onClick = { vm.onIntent(CrewPickerIntent.SubmitJoin) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            state.error?.let { err ->
                FrErrorBanner(
                    text = resolve(err.toStringKey()),
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}
