package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
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
        Box(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // === Hero ===
                FrLogo(size = 96.dp)
                FrText(
                    text = resolve(CrewStringKey.PickerBrandName),
                    style = MaterialTheme.typography.headlineSmall,
                )
                FrText(
                    text = resolve(CrewStringKey.PickerHeroSubtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.md))

                // === Content (crews OR empty copy) ===
                if (state.crews.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.crews.forEach { crew ->
                            FrButton(
                                label = resolve(CrewStringKey.PickerCrewButton, crew.name, crew.size),
                                onClick = { vm.onIntent(CrewPickerIntent.PickCrew(crew.id)) },
                                variant = FrButtonVariant.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else if (!state.showCreateForm && !state.showJoinForm) {
                    FrText(
                        text = resolve(CrewStringKey.PickerEmptySubtext),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // === CTAs ===
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                // === Inline forms (open one at a time; CTA toggles it) ===
                if (state.showCreateForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FrTextField(
                            value = state.createInput,
                            onValueChange = { vm.onIntent(CrewPickerIntent.CreateInputChanged(it)) },
                            label = resolve(CrewStringKey.CreateNameLabel),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FrButton(
                            label = resolve(CrewStringKey.CreateSubmit),
                            onClick = { vm.onIntent(CrewPickerIntent.SubmitCreate) },
                            variant = FrButtonVariant.Primary,
                        )
                    }
                }
                if (state.showJoinForm) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FrTextField(
                            value = state.joinInput,
                            onValueChange = { vm.onIntent(CrewPickerIntent.JoinInputChanged(it)) },
                            label = resolve(CrewStringKey.JoinCodeLabel),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FrButton(
                            label = resolve(CrewStringKey.JoinSubmit),
                            onClick = { vm.onIntent(CrewPickerIntent.SubmitJoin) },
                            variant = FrButtonVariant.Primary,
                        )
                    }
                }

                state.error?.let { err ->
                    FrErrorBanner(text = resolve(err.toStringKey()))
                }
            }
        }
    }
}
