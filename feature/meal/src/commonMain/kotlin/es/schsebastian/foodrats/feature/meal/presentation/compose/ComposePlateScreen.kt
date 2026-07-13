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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
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
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.toFixed
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.decodeImageBitmap
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * Longest-side cap for the composer's decoded plate preview. The upload bytes stay at full
 * resolution (2048px); only the on-screen decode is downsampled — Android via
 * `BitmapFactory.inSampleSize` (2048 → 1024, a 4x smaller bitmap), iOS via a post-decode scale.
 */
private const val PLATE_DECODE_MAX_DIMENSION = 1024

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
    // Decode OFF the main thread: the synchronous full-res decode (bytes are capped at 2048px,
    // ~16 MB of ARGB) used to run in composition and froze the first frame on every entry and
    // recapture. produceState keys on the photo's content hash (recomposition can hand an
    // equal-but-distinct array — same convention as the classification LaunchedEffect above)
    // and shows the theme-adaptive field floor / no hero until the bitmap lands.
    // Dispatchers.Default is used directly rather than DispatcherProvider because this is a
    // presentation-layer CPU-bound decode, not a data-layer IO boundary (the house
    // one-withContext-per-repository-method rule governs repositories), and Default exists on
    // every KMP target. The decode is also downsampled to display size (PLATE_DECODE_MAX_DIMENSION):
    // the sharp copy is a 300dp-tall crop and the floor copy is heavily blurred, so 1024px is
    // more than enough and quarters the retained bitmap.
    val plateState by produceState<ImageBitmap?>(initialValue = null, bytes?.contentHashCode()) {
        value = bytes?.let { withContext(Dispatchers.Default) { decodeImageBitmap(it, PLATE_DECODE_MAX_DIMENSION) } }
    }
    // Plain local so the null-checks below smart-cast (a delegated property can't).
    val plate = plateState
    val floorPainter = remember(plate) { plate?.let { BitmapPainter(it) } }

    // When a photo is present the floor is always dark-scrimmed (photo dim+scrim), so white onMedia
    // text is always correct in both themes. When there is NO photo the floor is adaptive (light in
    // light mode, dark in dark mode) and the text must use the theme-aware foreground color instead.
    val onFloorColor = if (floorPainter != null) StructuralColors.onMedia else StructuralColors.foreground

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — the captured plate, blurred, IS the floor. Falls back to a theme-adaptive field floor
        // pre-capture so the empty screen reads correctly in both light and dark themes.
        if (floorPainter != null) {
            FrMediaFloor(painter = floorPainter, blur = StructuralBlur.Heavy, dim = 0.55f, scrim = FrScrimStyle.Even)
        } else {
            FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, tone = FrFloorTone.Adaptive)
        }

        // Z2 — transparent scrolling content plane.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .imePadding()
                .frSafeHorizontalPadding()
                .frContentWidth(Breakpoints.formMax)
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.lg)) // breathing room below the status bar

            FrEyebrow(text = resolve(MealStringKey.ComposeEyebrow).uppercase(), color = onFloorColor.copy(alpha = 0.85f))
            Spacer(Modifier.height(Spacing.xs))
            FrText(
                text = resolve(MealStringKey.ComposeTitle),
                style = StructuralType.titleXl,
                color = onFloorColor,
                modifier = Modifier.semantics { heading() },
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
                        // Confirm to screen readers that a photo is attached (the blurred floor copy stays
                        // decorative so the same image isn't announced twice).
                        contentDescription = resolve(MealStringKey.ComposePhotoDescription),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.classifying) {
                        FrStructuralChip(
                            label = resolve(MealStringKey.IngredientsClassifying),
                            tone = FrChipTone.Ember,
                            leadingIcon = FrIcons.Star,
                            compact = true,
                            // Announce "Analyzing ingredients…" when the AI starts looking at the plate.
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(Spacing.sm)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    // Non-removable provenance marker: a gallery-sourced plate is permanently
                    // labelled so every crew member can tell it wasn't shot live. No onClick —
                    // the marker cannot be dismissed or toggled.
                    if (state.plateSource == PlateSource.Gallery) {
                        val galleryA11y = resolve(MealStringKey.ComposeGalleryChipA11y)
                        FrStructuralChip(
                            label = resolve(MealStringKey.ComposeGalleryChip),
                            leadingIcon = FrIcons.GalleryImport,
                            compact = true,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm)
                                .semantics { contentDescription = galleryA11y },
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
            // Advisory: the AI couldn't detect ingredients. Muted (not a crimson DangerBanner) because
            // classification never gates publishing — the user can still add ingredients by hand.
            AnimatedVisibility(
                visible = state.classifierError != null && !state.classifying,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                state.classifierError?.let {
                    FrText(
                        text = resolve(it.toStringKey()),
                        style = StructuralType.micro,
                        color = onFloorColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            // Slot — an OPTIONAL "meal moment" label. Tapping a chip toggles it (tap again to clear).
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                FrEyebrow(
                    text = resolve(MealStringKey.ComposeSlotLabel).uppercase(),
                    color = onFloorColor.copy(alpha = 0.85f),
                    modifier = Modifier.semantics { heading() },
                )
                FrEyebrow(text = resolve(MealStringKey.ComposeSlotOptional).uppercase(), color = onFloorColor.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MealSlot.entries.forEach { slot ->
                    FrStructuralChip(
                        label = resolve(slot.slotLabel()),
                        selected = slot == state.selectedSlot,
                        onClick = { vm.onIntent(ComposePlateIntent.SelectSlot(slot)) },
                    )
                }
            }
            // The author has reached the per-crew daily cap in every selected crew: Continue is
            // gated. Explain why instead of leaving them stuck.
            AnimatedVisibility(
                visible = state.dailyLimitReached,
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
                FrEyebrow(
                    text = resolve(MealStringKey.ComposeAudienceLabel).uppercase(),
                    color = onFloorColor.copy(alpha = 0.85f),
                    modifier = Modifier.semantics { heading() },
                )
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
                label = resolve(MealStringKey.ComposeDishLabel),
                onMedia = floorPainter != null,
            )
            CharacterCounter(
                current = state.dish.length,
                max = DishName.MAX_LEN,
                overLimit = state.dishTooLong,
                color = onFloorColor,
                keyText = resolve(MealStringKey.ComposeDishCounter, state.dish.length, DishName.MAX_LEN),
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
                label = resolve(MealStringKey.ComposeDescriptionLabel),
                singleLine = false,
                onMedia = floorPainter != null,
            )
            CharacterCounter(
                current = state.descriptionInput.length,
                max = Description.MAX_LEN,
                overLimit = state.descriptionTooLong,
                color = onFloorColor,
                keyText = resolve(
                    MealStringKey.ComposeDescriptionCounter,
                    state.descriptionInput.length,
                    Description.MAX_LEN,
                ),
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
            // Clear the bottom action bar (Close · Continue) so the last content
            // (e.g. the error banner) is never hidden behind it.
            Spacer(Modifier.height(104.dp))
            Spacer(Modifier.navigationBarsPadding())
        }

        // Action bar — back/close (left) · continue (right). Pinned to the BOTTOM and
        // lifted above the soft keyboard via imePadding() so it stays visible while the
        // user types the dish title/description. A TOP bar gets shoved off-screen when
        // the iOS keyboard pans the scene (CMP 1.11 has no onFocusBehavior to disable
        // that); a bottom bar with imePadding() rides just above the keyboard instead —
        // the same pattern the MealDetail comment composer uses.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .frSafeHorizontalPadding()
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
    MealSlot.Brunch -> MealStringKey.SlotBrunch
    MealSlot.Lunch -> MealStringKey.SlotLunch
    MealSlot.Snack -> MealStringKey.SlotSnack
    MealSlot.Merienda -> MealStringKey.SlotMerienda
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
 * wrapper so the call sites stay terse and consistent. Takes the [label] in natural case: it's
 * uppercased for the visual eyebrow but passed verbatim as the field's accessible name so screen
 * readers read "Dish" rather than spelling out "D-I-S-H".
 */
@Composable
private fun FrUnderlineFieldLabeled(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    onMedia: Boolean = false,
) {
    es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField(
        value = value,
        onValueChange = onValueChange,
        label = label.uppercase(),
        accessibilityLabel = label,
        singleLine = singleLine,
        // When a plate photo is the floor it is always dark-scrimmed → white field text is correct in
        // both themes. When there is no photo the floor is adaptive and field text follows the theme.
        onMedia = onMedia,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The `N / MAX` character counter under a text field. The over-limit cue is NOT color-only (WCAG 1.4.1):
 * the field's danger banner carries the textual "too long" message; here a screen-reader-friendly
 * `contentDescription` ("N of MAX characters used") replaces the literal "N / MAX" glyph reading.
 */
@Composable
private fun CharacterCounter(
    current: Int,
    max: Int,
    overLimit: Boolean,
    color: androidx.compose.ui.graphics.Color,
    keyText: String,
) {
    val a11y = resolve(MealStringKey.ComposeCounterA11y, current, max)
    FrText(
        text = keyText,
        style = StructuralType.micro,
        color = color.copy(alpha = if (overLimit) 1f else 0.6f),
        modifier = Modifier
            .padding(top = Spacing.xs)
            .semantics { contentDescription = a11y },
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
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            // Announce the message when the banner appears (WCAG 4.1.3) — a blocked publish / moderation
            // hit / daily-cap hit must reach TalkBack/VoiceOver without the user re-traversing the screen.
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(image = FrIcons.Warning, contentDescription = null, tint = semantic.onDanger)
        FrText(text = text, color = semantic.onDanger, style = StructuralType.body)
    }
}
