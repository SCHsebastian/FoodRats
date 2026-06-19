package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
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
        // Keyboard handling: at rest the form sits vertically centered. When the
        // IME opens, the scaffold's safeDrawing inset shrinks the viewport — keeping
        // it centered would re-center and lift the whole hero + field upward (the
        // "everything moves up" bug). Instead pin to the top once the keyboard is
        // up and let the scrollable Box bring the focused field above the keyboard.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            contentAlignment = if (imeVisible) Alignment.TopCenter else Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.frContentWidth(Breakpoints.formMax),
            ) {
                // === Hero ===
                // Signature: the logo "develops in" with a gentle focal reveal, then the
                // wordmark + tagline rise in just behind it for a layered entrance.
                FrLogo(size = 96.dp, modifier = Modifier.frRevealScale())
                FrText(
                    text = resolve(CrewStringKey.PickerBrandName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.frRiseIn(delayMillis = 60),
                )
                FrText(
                    text = resolve(CrewStringKey.PickerHeroSubtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.frRiseIn(delayMillis = 110),
                )

                Spacer(Modifier.height(Spacing.sm))

                // === Content (skeleton on first load OR crews OR empty copy) ===
                val crews = state.crews
                val formOpen = state.showCreateForm || state.showJoinForm
                if (crews.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        // Signature: the crew list cascades in — each button rises just
                        // after the one above, with a small stagger so longer lists still
                        // settle promptly.
                        crews.forEachIndexed { index, crew ->
                            FrButton(
                                label = resolve(CrewStringKey.PickerCrewButton, crew.name, crew.size),
                                onClick = { vm.onIntent(CrewPickerIntent.PickCrew(crew.id)) },
                                variant = FrButtonVariant.Secondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .frRiseIn(delayMillis = (index % 6) * 40),
                            )
                        }
                    }
                } else if (state.isLoading && !formOpen) {
                    CrewListSkeleton()
                } else if (!formOpen) {
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

/**
 * Decorative initial-load placeholder: three full-width, button-height shimmer bars
 * mimicking the eventual crew-button list. Shown only before the first crews emission.
 */
@Composable
private fun CrewListSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(3) {
            FrShimmerBox(
                modifier = Modifier.fillMaxWidth().height(Sizes.touchTarget),
                shape = RoundedCornerShape(Radius.md),
            )
        }
    }
}
