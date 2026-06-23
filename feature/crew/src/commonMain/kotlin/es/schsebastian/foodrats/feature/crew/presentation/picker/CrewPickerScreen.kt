package es.schsebastian.foodrats.feature.crew.presentation.picker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrLogo
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * Structural crew picker: a centered FoodRats brand hero over a warm [FrMediaFloor] (`fieldFloor`),
 * the account's crews as tappable frosted [FrGlassTile] rows, and Create / Join glass CTAs that toggle
 * inline [FrUnderlineField] forms. Root destination (no back chrome). All ViewModel wiring preserved;
 * the IME-centering behaviour is kept (centered at rest, pinned to top once the keyboard is up).
 */
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

    Box(modifier = Modifier.fillMaxSize()) {
        FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, dim = 0.32f, scrim = FrScrimStyle.Even)

        // Keyboard handling: at rest the form sits vertically centered; once the IME opens, pin to the
        // top and let the scrollable column lift the focused field above the keyboard (avoids the
        // "everything moves up" re-centering bug).
        // Overflow handling: a vertically-scrolling Box with `Alignment.Center` keeps a taller-than-
        // viewport child centered, so its top is clipped and the bottom CTAs ("Create" / "Join") sit
        // below the fold with no way to reach them. Once there are crews (the tall case), anchor to
        // TopCenter so the column grows downward and the scroll reveals the CTAs. Keep the centered
        // look only for the short empty/loading state.
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val contentCanOverflow = imeVisible || state.crews.isNotEmpty()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            contentAlignment = if (contentCanOverflow) Alignment.TopCenter else Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.frContentWidth(Breakpoints.formMax),
            ) {
                // === Brand hero ===
                FrLogo(size = 92.dp, modifier = Modifier.frRevealScale())
                FrText(
                    text = resolve(CrewStringKey.PickerBrandName),
                    style = StructuralType.metricMd,
                    color = StructuralColors.foreground,
                    modifier = Modifier.frRiseIn(delayMillis = 60),
                )
                FrText(
                    text = resolve(CrewStringKey.PickerHeroSubtitle),
                    style = StructuralType.body,
                    color = StructuralColors.foreground.copy(alpha = 0.82f),
                    modifier = Modifier.frRiseIn(delayMillis = 110),
                )

                Spacer(Modifier.height(Spacing.md))

                // === Content: crews / skeleton / empty copy ===
                val crews = state.crews
                val formOpen = state.showCreateForm || state.showJoinForm
                if (crews.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FrEyebrow(
                            text = resolve(CrewStringKey.PickerCrewsLabel).uppercase(),
                            modifier = Modifier.frRiseIn(delayMillis = 140),
                        )
                        crews.forEachIndexed { index, crew ->
                            CrewRow(
                                crew = crew,
                                ringIndex = index,
                                onClick = { vm.onIntent(CrewPickerIntent.PickCrew(crew.id)) },
                                modifier = Modifier.frRiseIn(delayMillis = 160 + (index % 6) * 40),
                            )
                        }
                    }
                } else if (state.isLoading && !formOpen) {
                    CrewListSkeleton()
                } else if (!formOpen) {
                    FrText(
                        text = resolve(CrewStringKey.PickerEmptySubtext),
                        style = StructuralType.body,
                        color = StructuralColors.foreground.copy(alpha = 0.7f),
                    )
                }

                // === CTAs ===
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrGlassButton(
                        label = resolve(CrewStringKey.PickerCreateCta),
                        onClick = { vm.onIntent(CrewPickerIntent.ToggleCreateForm) },
                        tone = if (state.showCreateForm) FrButtonTone.Primary else FrButtonTone.Glass,
                        modifier = Modifier.weight(1f),
                    )
                    FrGlassButton(
                        label = resolve(CrewStringKey.PickerJoinCta),
                        onClick = { vm.onIntent(CrewPickerIntent.ToggleJoinForm) },
                        tone = if (state.showJoinForm) FrButtonTone.Primary else FrButtonTone.Glass,
                        modifier = Modifier.weight(1f),
                    )
                }

                // === Inline forms (one at a time; CTA toggles it) ===
                if (state.showCreateForm) {
                    FormTile {
                        FrUnderlineField(
                            value = state.createInput,
                            onValueChange = { vm.onIntent(CrewPickerIntent.CreateInputChanged(it)) },
                            label = resolve(CrewStringKey.CreateNameLabel).uppercase(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FrGlassButton(
                            label = resolve(CrewStringKey.CreateSubmit),
                            onClick = { vm.onIntent(CrewPickerIntent.SubmitCreate) },
                            tone = FrButtonTone.Primary,
                            fillWidth = true,
                        )
                    }
                }
                if (state.showJoinForm) {
                    FormTile {
                        FrUnderlineField(
                            value = state.joinInput,
                            onValueChange = { vm.onIntent(CrewPickerIntent.JoinInputChanged(it)) },
                            label = resolve(CrewStringKey.JoinCodeLabel).uppercase(),
                            // Invite codes are 6 uppercase chars — capitalize all input so it matches.
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FrGlassButton(
                            label = resolve(CrewStringKey.JoinSubmit),
                            onClick = { vm.onIntent(CrewPickerIntent.SubmitJoin) },
                            tone = FrButtonTone.Primary,
                            fillWidth = true,
                        )
                    }
                }

                state.error?.let { err ->
                    DangerBanner(text = resolve(err.toStringKey()))
                }
            }
        }
    }
}

/** A tappable frosted crew row: ringed avatar + name + member-count micro + chevron. */
@Composable
private fun CrewRow(
    crew: Crew,
    ringIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = listOf(FrAvatarRing.Moss, FrAvatarRing.Ember, FrAvatarRing.Rust)[ringIndex % 3]
    FrGlassTile(depth = FrTileDepth.Near, onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrGlassAvatar(initials = crew.name, ring = ring, size = 48.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                FrText(text = crew.name, style = StructuralType.titleMd, color = StructuralColors.foreground)
                FrText(
                    text = resolve(CrewStringKey.SettingsMembersCount, crew.size).uppercase(),
                    style = StructuralType.micro,
                    color = StructuralColors.foreground.copy(alpha = 0.6f),
                )
            }
            FrIcon(
                image = FrIcons.ChevronRight,
                contentDescription = null,
                tint = StructuralColors.foreground.copy(alpha = 0.4f),
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
    }
}

@Composable
private fun FormTile(content: @Composable () -> Unit) {
    FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            content()
        }
    }
}

@Composable
private fun DangerBanner(text: String) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(semantic.danger)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(image = FrIcons.Warning, contentDescription = null, tint = semantic.onDanger)
        FrText(text = text, color = semantic.onDanger, style = StructuralType.body)
    }
}

/**
 * Decorative initial-load placeholder: three full-width, button-height shimmer bars mimicking the
 * eventual crew list. Shown only before the first crews emission.
 */
@Composable
private fun CrewListSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(3) {
            FrShimmerBox(
                modifier = Modifier.fillMaxWidth().height(Sizes.touchTarget),
                shape = RoundedCornerShape(Radius.lg),
            )
        }
    }
}
