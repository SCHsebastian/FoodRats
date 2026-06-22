package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.structural.FrChipTone
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrFloorTone
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.toFixed
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.decodeImageBitmap
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * Structural "compose your plate" screen: the captured plate IS the blurred floor; a zero-chrome
 * content plane scrolls over it. Floating glass chrome hovers on top (close · publish). Pick a slot,
 * confirm AI-detected ingredients, name the dish, add a description + optional location pin, then
 * Continue → [FrConfirmDialog] → the upload is enqueued (fire-and-forget) and the user is sent back
 * to the feed. All VM wiring is preserved; only the visual layer is structural.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposePlateScreen(
    onPublishStarted: () -> Unit,
    onEditIngredients: () -> Unit,
    onClose: () -> Unit,
    vm: ComposePlateViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val coordinatesLabel = state.coordinates?.let { coords ->
        resolve(MealStringKey.ComposeCoordinatesFormat, coords.latitude.toFixed(5), coords.longitude.toFixed(5))
    }

    LaunchedEffect(Unit) {
        vm.effects.collect {
            if (it is ComposePlateEffect.UploadEnqueued) onPublishStarted()
        }
    }

    // Kick off on-device classification when a plate arrives. The VM dedupes by photo content, so
    // re-entry is a no-op and re-capture re-classifies. Key on the content hash, not the ByteArray
    // identity — recomposition can hand us an equal-but-distinct array.
    LaunchedEffect(state.photoBytes?.contentHashCode()) {
        state.photoBytes?.let { vm.onPhotoCaptured(it) }
    }

    val bytes = state.photoBytes
    val plate = remember(bytes) { bytes?.let { decodeImageBitmap(it) } }
    val floorPainter = remember(plate) { plate?.let { BitmapPainter(it) } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — the captured plate, blurred, IS the floor. Falls back to a warm taco mood pre-capture.
        if (floorPainter != null) {
            FrMediaFloor(painter = floorPainter, blur = StructuralBlur.Heavy, dim = 0.55f, scrim = FrScrimStyle.Even)
        } else {
            FrMediaFloor(brush = StructuralColors.dishTacos, blur = StructuralBlur.Soft, dim = 0.42f, scrim = FrScrimStyle.Even, tone = FrFloorTone.OnMedia)
        }

        // Z2 — transparent scrolling content plane.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(64.dp)) // clear the floating chrome row

            FrEyebrow(text = resolve(MealStringKey.ComposeEyebrow).uppercase(), color = StructuralColors.onMedia.copy(alpha = 0.85f))
            Spacer(Modifier.height(Spacing.xs))
            FrText(
                text = resolve(MealStringKey.ComposeTitle),
                style = StructuralType.titleXl,
                color = StructuralColors.onMedia,
            )
            Spacer(Modifier.height(Spacing.lg))

            // The captured plate, sharp, as a hero stratum.
            if (plate != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(Radius.lg)),
                ) {
                    Image(
                        bitmap = plate,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.classifying) {
                        FrStructuralChip(
                            label = resolve(MealStringKey.IngredientsClassifying),
                            tone = FrChipTone.Ember,
                            leadingIcon = FrIcons.Star,
                            compact = true,
                            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // Ingredients (tap to confirm/edit on the picker screen).
            FrGlassTile(depth = FrTileDepth.Deep, onClick = onEditIngredients) {
                TileRow(
                    icon = FrIcons.Eco,
                    title = when {
                        state.classifying -> resolve(MealStringKey.IngredientsClassifying)
                        state.draftIngredients.isNotEmpty() ->
                            resolve(MealStringKey.IngredientsRowSummary, state.draftIngredients.size)
                        else -> resolve(MealStringKey.IngredientsRowAdd)
                    },
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // Slot.
            FrEyebrow(text = resolve(MealStringKey.ComposeSlotLabel).uppercase(), color = StructuralColors.onMedia.copy(alpha = 0.85f))
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MealSlot.entries.forEach { slot ->
                    val taken = slot in state.takenSlots && slot != state.selectedSlot
                    FrStructuralChip(
                        label = resolve(slot.slotLabel()),
                        selected = slot == state.selectedSlot,
                        modifier = if (taken) Modifier.alpha(0.4f) else Modifier,
                        onClick = if (taken) null else ({ vm.onIntent(ComposePlateIntent.SelectSlot(slot)) }),
                    )
                }
            }
            // Every slot is taken today: the VM falls back to a taken slot, so the selected slot is
            // itself taken and Continue is gated. Explain why instead of leaving the author stuck.
            AnimatedVisibility(
                visible = state.selectedSlot in state.takenSlots,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                DangerBanner(
                    text = resolve(MealStringKey.ComposeAllSlotsTaken),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // Audience: which crews this plate is shared with. Hidden for single-crew authors.
            if (state.showCrewPicker) {
                FrEyebrow(text = resolve(MealStringKey.ComposeAudienceLabel).uppercase(), color = StructuralColors.onMedia.copy(alpha = 0.85f))
                Spacer(Modifier.height(Spacing.sm))
                val allSelected = state.availableCrews.isNotEmpty() &&
                    state.availableCrews.all { it.id in state.selectedCrewIds }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FrStructuralChip(
                        label = resolve(MealStringKey.ComposeAudienceAll),
                        selected = allSelected,
                        onClick = { vm.onIntent(ComposePlateIntent.AllCrewsSelected) },
                    )
                    state.availableCrews.forEach { crew ->
                        FrStructuralChip(
                            label = crew.name,
                            selected = !allSelected && crew.id in state.selectedCrewIds,
                            onClick = { vm.onIntent(ComposePlateIntent.CrewToggled(crew.id)) },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // Dish.
            FrUnderlineFieldLabeled(
                value = state.dish,
                onValueChange = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
                label = resolve(MealStringKey.ComposeDishLabel).uppercase(),
            )
            AnimatedVisibility(
                visible = state.dishWarning,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                DangerBanner(
                    text = resolve(MealStringKey.DishModerationWarning),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // Description + counter.
            FrUnderlineFieldLabeled(
                value = state.descriptionInput,
                onValueChange = { vm.onIntent(ComposePlateIntent.DescriptionChanged(it)) },
                label = resolve(MealStringKey.ComposeDescriptionLabel).uppercase(),
                singleLine = false,
            )
            FrText(
                text = resolve(
                    MealStringKey.ComposeDescriptionCounter,
                    state.descriptionInput.length,
                    Description.MAX_LEN,
                ),
                style = StructuralType.micro,
                color = StructuralColors.onMedia.copy(alpha = if (state.descriptionTooLong) 1f else 0.6f),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            AnimatedVisibility(
                visible = state.descriptionWarning,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                DangerBanner(
                    text = resolve(MealStringKey.DescriptionModerationWarning),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            // Location pin.
            val pinned = coordinatesLabel != null
            FrGlassTile(
                depth = FrTileDepth.Deep,
                onClick = if (state.locating || pinned) null else ({ vm.onIntent(ComposePlateIntent.RequestLocation) }),
            ) {
                TileRow(
                    icon = FrIcons.Place,
                    title = when {
                        state.locating -> resolve(MealStringKey.ComposeLocating)
                        pinned -> coordinatesLabel!!
                        else -> resolve(MealStringKey.ComposeAddLocation)
                    },
                    trailing = {
                        if (pinned) {
                            FrGlassCircleButton(
                                icon = FrIcons.Close,
                                onClick = { vm.onIntent(ComposePlateIntent.ClearLocation) },
                                contentDescription = resolve(MealStringKey.ComposeClearLocation),
                                size = 36.dp,
                            )
                        } else {
                            Chevron()
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                state.error?.let {
                    DangerBanner(text = resolve(it.toStringKey()), modifier = Modifier.padding(top = Spacing.lg))
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            Spacer(Modifier.navigationBarsPadding())
        }

        // Floating chrome — close (left) · publish (right), hovering over the plate floor.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Close,
                onClick = onClose,
                contentDescription = resolve(CommonStringKey.Cancel),
            )
            FrGlassButton(
                label = resolve(CommonStringKey.Continue),
                onClick = { vm.onIntent(ComposePlateIntent.RequestConfirm) },
                tone = FrButtonTone.Primary,
                enabled = state.canContinue,
                compact = true,
            )
        }
    }

    if (state.showConfirm) {
        FrConfirmDialog(
            title = resolve(MealStringKey.PublishConfirmTitle),
            message = resolve(MealStringKey.PublishConfirmMessage),
            confirmLabel = resolve(MealStringKey.PublishConfirmCta),
            dismissLabel = resolve(CommonStringKey.Cancel),
            onConfirm = { vm.onIntent(ComposePlateIntent.ConfirmPublish) },
            onDismiss = { vm.onIntent(ComposePlateIntent.DismissConfirm) },
        )
    }
}

private fun MealSlot.slotLabel(): MealStringKey = when (this) {
    MealSlot.Breakfast -> MealStringKey.SlotBreakfast
    MealSlot.Lunch -> MealStringKey.SlotLunch
    MealSlot.Dinner -> MealStringKey.SlotDinner
}

/**
 * A single tappable glass-tile row: an olive icon badge, a title that grows to fill, and a trailing
 * affordance (chevron / clear button). The structural replacement for the old matte list rows.
 */
@Composable
private fun TileRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(scheme.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(image = icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconMd))
        }
        FrText(
            text = title,
            style = StructuralType.titleMd,
            color = StructuralColors.foreground,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun Chevron() {
    FrIcon(
        image = FrIcons.ChevronRight,
        contentDescription = null,
        tint = StructuralColors.foreground.copy(alpha = 0.4f),
        modifier = Modifier.size(Sizes.iconMd),
    )
}

/**
 * The structural [FrUnderlineField] wrapped to always span the content width. Kept as a thin local
 * wrapper so the call sites stay terse and consistent.
 */
@Composable
private fun FrUnderlineFieldLabeled(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
) {
    es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        // The composer's floor is the captured plate photo (dark-scrimmed in both themes) — keep the
        // field content white so it stays legible in light mode.
        onMedia = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * HARD-BLOCK / error banner. Crimson `danger` tone — it either gates publishing (UGC moderation §3)
 * or surfaces a publish error the author must resolve.
 */
@Composable
private fun DangerBanner(text: String, modifier: Modifier = Modifier) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = modifier
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
