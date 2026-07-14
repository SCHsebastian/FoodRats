package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassDialog
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
import es.schsebastian.foodrats.core.domain.crew.CrewSummary
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.DishName
import es.schsebastian.foodrats.core.domain.meal.MealPublishPolicy
import es.schsebastian.foodrats.core.domain.meal.MealSlot
import es.schsebastian.foodrats.core.domain.meal.PlateSource
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.toFixed
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoPickResult
import es.schsebastian.foodrats.core.presentation.photopicker.PickedPhoto
import es.schsebastian.foodrats.core.presentation.photopicker.rememberPhotoPicker
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.domain.model.Plate
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.rememberDecodedBitmap
import es.schsebastian.foodrats.feature.meal.presentation.components.resizeForUpload
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * Longest-side cap for the composer's decoded plate preview. The upload bytes stay at full
 * resolution (2048px); only the on-screen decode is downsampled — Android via
 * `BitmapFactory.inSampleSize` (2048 → 1024, a 4x smaller bitmap), iOS via a post-decode scale.
 */
private const val PLATE_DECODE_MAX_DIMENSION = 1024

/**
 * Longest-side cap for a single strip thumbnail's decoded bitmap. Each tile renders at
 * [es.schsebastian.foodrats.core.designsystem.tokens.Sizes.feedRowThumbnail] (76dp), so a
 * decode this small easily covers any density while keeping up to 10 concurrently-decoded
 * thumbnails cheap.
 */
private const val THUMBNAIL_DECODE_MAX_DIMENSION = 200

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
    val scope = rememberCoroutineScope()
    // The "add another photo" chooser (decision 2): tapping the strip's add tile shows this, offering
    // Camera (single shot) or Gallery (multi-select up to the remaining capacity). Purely ephemeral
    // popup-visibility UI state — mirrors CaptureMealScreen's local `awaitingChoice`, not MVI state.
    var showAddPhotoDialog by remember { mutableStateOf(false) }
    val addPhotoPicker = rememberPhotoPicker { result ->
        when (result) {
            is PhotoPickResult.Picked -> scope.launch {
                val bytes = withContext(Dispatchers.Default) { result.bytes.resizeForUpload() }
                vm.onIntent(ComposePlateIntent.AddPhotos(listOf(PickedPhoto(bytes, result.source, result.metadata))))
            }
            is PhotoPickResult.PickedMultiple -> scope.launch {
                val resized = result.photos.map { photo ->
                    val bytes = withContext(Dispatchers.Default) { photo.bytes.resizeForUpload() }
                    PickedPhoto(bytes, photo.source, photo.metadata)
                }
                vm.onIntent(ComposePlateIntent.AddPhotos(resized))
            }
            PhotoPickResult.Cancelled -> Unit
            is PhotoPickResult.Failed -> vm.onIntent(ComposePlateIntent.PhotoPickFailed)
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect {
            if (it is ComposePlateEffect.UploadEnqueued) onPublishStarted()
        }
    }

    // Kick off on-device classification when a plate arrives. The VM dedupes by photo content, so
    // re-entry is a no-op and re-capture re-classifies. Key on the content hash, not the ByteArray
    // identity — recomposition can hand us an equal-but-distinct array. ALWAYS the primary (index 0)
    // photo, independent of which photo is currently selected/previewed in the strip.
    val primaryHash = remember(state.primaryPhoto?.photoBytes) { state.primaryPhoto?.photoBytes?.contentHashCode() }
    LaunchedEffect(primaryHash) {
        state.primaryPhoto?.photoBytes?.let { vm.onPhotoCaptured(it) }
    }

    // The hero preview shows the SELECTED photo (defaults to the primary), not necessarily the one
    // being classified — see the LaunchedEffect above.
    val bytes = state.selectedPhoto?.photoBytes
    // Decode OFF the main thread: the synchronous full-res decode (bytes are capped at 2048px,
    // ~16 MB of ARGB) used to run in composition and froze the first frame on every entry and
    // recapture. rememberDecodedBitmap keys on the photo's content hash (recomposition can hand
    // an equal-but-distinct array — same convention as the classification LaunchedEffect above)
    // and shows the theme-adaptive field floor / no hero until the bitmap lands. The decode is
    // also downsampled to display size (PLATE_DECODE_MAX_DIMENSION): the sharp copy is a 300dp-tall
    // crop and the floor copy is heavily blurred, so 1024px is more than enough and quarters the
    // retained bitmap.
    val plate = rememberDecodedBitmap(bytes, PLATE_DECODE_MAX_DIMENSION)
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
                    // Keyed to the PRIMARY photo (index 0), not the selected one: classification
                    // always runs against plates[0] (see the LaunchedEffect above), so the "analyzing"
                    // badge only makes sense while that same photo is the one on screen — otherwise a
                    // user browsing photo 3 while photo 1 classifies in the background would see a
                    // misleading "analyzing" badge on a photo that isn't actually being analyzed.
                    if (state.classifying && state.selectedIndex == 0) {
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
                    // the marker cannot be dismissed or toggled. Reflects the SELECTED photo (each
                    // photo carries its own source); the strip's own tiles carry a matching mini marker.
                    if (state.selectedPhoto?.source == PlateSource.Gallery) {
                        val galleryA11y = resolve(MealStringKey.ComposeGalleryChipA11y)
                        FrStructuralChip(
                            label = resolve(MealStringKey.ComposeGalleryChip),
                            leadingIcon = FrIcons.GalleryImport,
                            compact = true,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm)
                                .semantics(mergeDescendants = true) { contentDescription = galleryA11y },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // Photo strip (Wave 3): every draft photo in order, a "N / 10" counter, and — for the
            // selected tile — move-left / move-right / remove controls. Shown whenever there's at
            // least one photo OR room to add one (an empty draft mid-add still wants the add tile).
            if (state.photos.isNotEmpty() || state.canAddMorePhotos) {
                PhotoStrip(
                    photos = state.photos,
                    selectedIndex = state.selectedIndex,
                    canAddMore = state.canAddMorePhotos,
                    onFloorColor = onFloorColor,
                    onSelect = { vm.onIntent(ComposePlateIntent.SelectPhoto(it)) },
                    onRemove = { vm.onIntent(ComposePlateIntent.RemovePhotoAt(it)) },
                    onMoveLeft = { vm.onIntent(ComposePlateIntent.MovePhoto(it, it - 1)) },
                    onMoveRight = { vm.onIntent(ComposePlateIntent.MovePhoto(it, it + 1)) },
                    onAddPhoto = { showAddPhotoDialog = true },
                )
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
            SlotSection(
                selectedSlot = state.selectedSlot,
                dailyLimitReached = state.dailyLimitReached,
                onFloorColor = onFloorColor,
                onSlotSelected = { vm.onIntent(ComposePlateIntent.SelectSlot(it)) },
            )
            Spacer(Modifier.height(Spacing.lg))

            // Audience: which crews this plate is shared with. Hidden for single-crew authors.
            if (state.showCrewPicker) {
                AudienceSection(
                    availableCrews = state.availableCrews,
                    selectedCrewIds = state.selectedCrewIds,
                    onFloorColor = onFloorColor,
                    onAllCrewsSelected = { vm.onIntent(ComposePlateIntent.AllCrewsSelected) },
                    onCrewToggled = { vm.onIntent(ComposePlateIntent.CrewToggled(it)) },
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            // Dish.
            DishSection(
                dish = state.dish,
                dishTooLong = state.dishTooLong,
                dishWarning = state.dishWarning,
                onMedia = floorPainter != null,
                onFloorColor = onFloorColor,
                onDishChanged = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
            )
            Spacer(Modifier.height(Spacing.lg))

            // Description + counter.
            DescriptionSection(
                description = state.descriptionInput,
                descriptionTooLong = state.descriptionTooLong,
                descriptionWarning = state.descriptionWarning,
                onMedia = floorPainter != null,
                onFloorColor = onFloorColor,
                onDescriptionChanged = { vm.onIntent(ComposePlateIntent.DescriptionChanged(it)) },
            )
            Spacer(Modifier.height(Spacing.lg))

            // Location pin.
            LocationSection(
                locating = state.locating,
                coordinatesLabel = coordinatesLabel,
                onRequestLocation = { vm.onIntent(ComposePlateIntent.RequestLocation) },
                onClearLocation = { vm.onIntent(ComposePlateIntent.ClearLocation) },
            )

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

    if (showAddPhotoDialog) {
        val remaining = (MealPublishPolicy.MAX_PHOTOS_PER_MEAL - state.photos.size).coerceAtLeast(1)
        AddPhotoSourceDialog(
            onCamera = { showAddPhotoDialog = false; addPhotoPicker.launchCamera() },
            onGallery = { showAddPhotoDialog = false; addPhotoPicker.launchGallery(maxItems = remaining) },
            onDismiss = { showAddPhotoDialog = false },
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
 * The "meal moment" chip row (optional — tapping the selected chip again clears it) plus the
 * daily-cap-reached banner. Extracted so a slot tap only recomposes this section, not the whole
 * screen body.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotSection(
    selectedSlot: MealSlot?,
    dailyLimitReached: Boolean,
    onFloorColor: Color,
    onSlotSelected: (MealSlot) -> Unit,
) {
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
                selected = slot == selectedSlot,
                onClick = { onSlotSelected(slot) },
            )
        }
    }
    // The author has reached the per-crew daily cap in every selected crew: Continue is
    // gated. Explain why instead of leaving them stuck.
    AnimatedVisibility(
        visible = dailyLimitReached,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        DangerBanner(
            text = resolve(MealStringKey.ComposeAllSlotsTaken),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/**
 * Which crews this plate is shared with. Only rendered by the caller when the author belongs to
 * more than one crew ([ComposePlateState.showCrewPicker]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudienceSection(
    availableCrews: List<CrewSummary>,
    selectedCrewIds: Set<CrewId>,
    onFloorColor: Color,
    onAllCrewsSelected: () -> Unit,
    onCrewToggled: (CrewId) -> Unit,
) {
    FrEyebrow(
        text = resolve(MealStringKey.ComposeAudienceLabel).uppercase(),
        color = onFloorColor.copy(alpha = 0.85f),
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(Spacing.sm))
    val allSelected = availableCrews.isNotEmpty() && availableCrews.all { it.id in selectedCrewIds }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FrStructuralChip(
            label = resolve(MealStringKey.ComposeAudienceAll),
            selected = allSelected,
            onClick = onAllCrewsSelected,
        )
        availableCrews.forEach { crew ->
            FrStructuralChip(
                label = crew.name,
                selected = !allSelected && crew.id in selectedCrewIds,
                onClick = { onCrewToggled(crew.id) },
            )
        }
    }
}

/** The dish-name field, its character counter, and the moderation-warning banner. */
@Composable
private fun DishSection(
    dish: String,
    dishTooLong: Boolean,
    dishWarning: Boolean,
    onMedia: Boolean,
    onFloorColor: Color,
    onDishChanged: (String) -> Unit,
) {
    FrUnderlineFieldLabeled(
        value = dish,
        onValueChange = onDishChanged,
        label = resolve(MealStringKey.ComposeDishLabel),
        onMedia = onMedia,
    )
    CharacterCounter(
        current = dish.length,
        max = DishName.MAX_LEN,
        overLimit = dishTooLong,
        color = onFloorColor,
        keyText = resolve(MealStringKey.ComposeDishCounter, dish.length, DishName.MAX_LEN),
    )
    AnimatedVisibility(
        visible = dishWarning,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        DangerBanner(
            text = resolve(MealStringKey.DishModerationWarning),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** The description field, its character counter, and the moderation-warning banner. */
@Composable
private fun DescriptionSection(
    description: String,
    descriptionTooLong: Boolean,
    descriptionWarning: Boolean,
    onMedia: Boolean,
    onFloorColor: Color,
    onDescriptionChanged: (String) -> Unit,
) {
    FrUnderlineFieldLabeled(
        value = description,
        onValueChange = onDescriptionChanged,
        label = resolve(MealStringKey.ComposeDescriptionLabel),
        singleLine = false,
        onMedia = onMedia,
    )
    CharacterCounter(
        current = description.length,
        max = Description.MAX_LEN,
        overLimit = descriptionTooLong,
        color = onFloorColor,
        keyText = resolve(MealStringKey.ComposeDescriptionCounter, description.length, Description.MAX_LEN),
    )
    AnimatedVisibility(
        visible = descriptionWarning,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        DangerBanner(
            text = resolve(MealStringKey.DescriptionModerationWarning),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

/** The optional location-pin tile: request/clear a coordinate, or show it once pinned. */
@Composable
private fun LocationSection(
    locating: Boolean,
    coordinatesLabel: String?,
    onRequestLocation: () -> Unit,
    onClearLocation: () -> Unit,
) {
    val pinned = coordinatesLabel != null
    FrGlassTile(
        depth = FrTileDepth.Deep,
        onClick = if (locating || pinned) null else onRequestLocation,
    ) {
        TileRow(
            icon = FrIcons.Place,
            title = when {
                locating -> resolve(MealStringKey.ComposeLocating)
                pinned -> coordinatesLabel!!
                else -> resolve(MealStringKey.ComposeAddLocation)
            },
            trailing = {
                if (pinned) {
                    FrGlassCircleButton(
                        icon = FrIcons.Close,
                        onClick = onClearLocation,
                        contentDescription = resolve(MealStringKey.ComposeClearLocation),
                        size = 36.dp,
                    )
                } else {
                    Chevron()
                }
            },
        )
    }
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
 * The multi-photo strip: an eyebrow header + "N / MAX" counter, a horizontal strip of every draft
 * photo in order plus a trailing add-tile (hidden once [canAddMore] is false), and — whenever
 * there's at least one photo — an action row scoped to [selectedIndex] (move-left / move-right /
 * remove). The large hero preview above this strip always mirrors [selectedIndex]; tapping a tile
 * here is the only way to change it directly (remove/move instead follow the affected photo).
 */
@Composable
private fun PhotoStrip(
    photos: List<Plate>,
    selectedIndex: Int,
    canAddMore: Boolean,
    onFloorColor: Color,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onAddPhoto: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrEyebrow(
                text = resolve(MealStringKey.ComposePhotosLabel).uppercase(),
                color = onFloorColor.copy(alpha = 0.85f),
                modifier = Modifier.semantics { heading() },
            )
            val counterA11y = resolve(
                MealStringKey.ComposePhotoCounterA11y, photos.size, MealPublishPolicy.MAX_PHOTOS_PER_MEAL,
            )
            FrText(
                text = resolve(MealStringKey.ComposePhotoCounter, photos.size, MealPublishPolicy.MAX_PHOTOS_PER_MEAL),
                style = StructuralType.micro,
                color = onFloorColor.copy(alpha = 0.6f),
                modifier = Modifier.semantics { contentDescription = counterA11y },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            itemsIndexed(photos, key = { index, plate -> photoStripKey(photos, index, plate) }) { index, plate ->
                PhotoStripTile(
                    plate = plate,
                    position = index + 1,
                    total = photos.size,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
            if (canAddMore) {
                item { AddPhotoTile(onClick = onAddPhoto) }
            }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            PhotoActionRow(
                position = selectedIndex + 1,
                total = photos.size,
                canMoveLeft = selectedIndex > 0,
                canMoveRight = selectedIndex < photos.size - 1,
                onFloorColor = onFloorColor,
                onRemove = { onRemove(selectedIndex) },
                onMoveLeft = { onMoveLeft(selectedIndex) },
                onMoveRight = { onMoveRight(selectedIndex) },
            )
        }
    }
}

/**
 * Stable [itemsIndexed] key for a strip photo, based on content rather than position — so a
 * MovePhoto/RemovePhotoAt keeps each tile's already-decoded thumbnail instead of Compose
 * recycling slots by index and re-decoding. [Plate] has no identity of its own (content-based
 * equals/hashCode), so two byte-identical photos in the same draft would collide on content hash
 * alone; disambiguate by counting how many equal-content photos precede this one.
 */
private fun photoStripKey(photos: List<Plate>, index: Int, plate: Plate): String {
    val hash = plate.photoBytes.contentHashCode()
    val occurrence = (0 until index).count { photos[it].photoBytes.contentHashCode() == hash }
    return "$hash-$occurrence"
}

/**
 * One thumbnail in the [PhotoStrip]. The whole tile is the tap target (selects it); a selected tile
 * gets a primary-colored border. A gallery-sourced photo carries its own mini provenance marker
 * (mirrors the hero's non-removable "Gallery" chip, at thumbnail scale) — reuses the same a11y
 * string as that hero chip since it conveys the identical fact about this specific photo.
 */
@Composable
private fun PhotoStripTile(
    plate: Plate,
    position: Int,
    total: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val thumbnail = rememberDecodedBitmap(plate.photoBytes, THUMBNAIL_DECODE_MAX_DIMENSION)
    val positionA11y = resolve(MealStringKey.ComposePhotoTileA11y, position, total)
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.md)
    val isSelected = selected
    Box(
        modifier = Modifier
            .size(Sizes.feedRowThumbnail)
            .clip(shape)
            .background(StructuralColors.tile)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            // A single explicit description per tile (position-based, so it's unique even when two
            // photos are byte-identical) — NOT merged with descendants, so the gallery marker below
            // stays independently announced/queryable rather than folded into this text.
            .semantics { contentDescription = positionA11y; this.selected = isSelected },
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (plate.source == PlateSource.Gallery) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(StructuralColors.glassButton),
                contentAlignment = Alignment.Center,
            ) {
                FrIcon(
                    image = FrIcons.GalleryImport,
                    contentDescription = resolve(MealStringKey.ComposeGalleryChipA11y),
                    tint = StructuralColors.foreground,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** The trailing "add another photo" tile — only rendered by [PhotoStrip] while under the photo cap. */
@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val a11y = resolve(MealStringKey.ComposeAddPhoto)
    FrGlassTile(
        depth = FrTileDepth.Deep,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(Sizes.feedRowThumbnail)
            .semantics { contentDescription = a11y },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FrIcon(
                image = FrIcons.AddPhoto,
                contentDescription = null,
                tint = StructuralColors.foreground,
                modifier = Modifier.size(Sizes.iconLg),
            )
        }
    }
}

/**
 * Controls for the currently-selected strip photo: move-left / move-right (disabled — not
 * hidden — at the ends of the list, so the row's width stays stable) and remove. The visible
 * "Photo N of M" label is the sighted-user counterpart of each tile's own position announcement.
 */
@Composable
private fun PhotoActionRow(
    position: Int,
    total: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onFloorColor: Color,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrText(
            text = resolve(MealStringKey.ComposePhotoTileA11y, position, total),
            style = StructuralType.micro,
            color = onFloorColor.copy(alpha = 0.6f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrGlassCircleButton(
                icon = FrIcons.ChevronLeft,
                onClick = onMoveLeft,
                contentDescription = resolve(MealStringKey.ComposeMovePhotoLeftA11y),
                size = 36.dp,
                enabled = canMoveLeft,
            )
            FrGlassCircleButton(
                icon = FrIcons.ChevronRight,
                onClick = onMoveRight,
                contentDescription = resolve(MealStringKey.ComposeMovePhotoRightA11y),
                size = 36.dp,
                enabled = canMoveRight,
            )
            FrGlassCircleButton(
                icon = FrIcons.Delete,
                onClick = onRemove,
                contentDescription = resolve(MealStringKey.ComposeRemovePhotoA11y),
                size = 36.dp,
                danger = true,
            )
        }
    }
}

/**
 * The Camera/Gallery chooser for adding another photo to an in-progress draft. A small
 * [FrGlassDialog] card over the platform [Dialog]'s own dimming scrim — mirrors the
 * `Dialog(onDismissRequest) { FrGlassDialog { ... } }` idiom already used elsewhere (e.g.
 * CrewSettingsScreen's invite/leave-owner dialogs) rather than CaptureMealScreen's full-screen
 * chooser, since this is an ADD to an existing draft, not the very first photo.
 */
@Composable
private fun AddPhotoSourceDialog(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        FrGlassDialog {
            FrText(
                text = resolve(MealStringKey.ComposeAddPhoto),
                style = StructuralType.titleMd,
                color = StructuralColors.foreground,
            )
            FrGlassButton(
                label = resolve(MealStringKey.CaptureRetryCamera),
                onClick = onCamera,
                tone = FrButtonTone.Primary,
                leadingIcon = FrIcons.Camera,
                fillWidth = true,
            )
            FrGlassButton(
                label = resolve(MealStringKey.CaptureChooseFromGallery),
                onClick = onGallery,
                tone = FrButtonTone.Glass,
                leadingIcon = FrIcons.GalleryImport,
                fillWidth = true,
            )
            FrGlassButton(
                label = resolve(CommonStringKey.Cancel),
                onClick = onDismiss,
                tone = FrButtonTone.Ghost,
                fillWidth = true,
            )
        }
    }
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
