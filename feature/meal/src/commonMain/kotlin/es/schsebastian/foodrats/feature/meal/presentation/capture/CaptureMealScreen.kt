package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrFloorTone
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoPickResult
import es.schsebastian.foodrats.core.presentation.photopicker.rememberPhotoPicker
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.resizeForUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CaptureMealScreen(
    onCaptured: () -> Unit,
    onCancelled: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CaptureMealViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    // True once the camera has been dismissed WITHOUT a photo (the user cancelled, or the platform
    // picker failed): rather than bouncing straight to onCancelled, offer a retry alongside a
    // gallery fallback so a dismissed camera isn't a dead end.
    var awaitingChoice by remember { mutableStateOf(false) }
    // True when the chooser was reached via PhotoPickResult.Failed (vs a plain dismiss): the
    // title then says the pick FAILED instead of the generic "add your photo", so a broken
    // pick is no longer indistinguishable from a deliberate cancel (review-track gap).
    var lastPickFailed by remember { mutableStateOf(false) }
    val picker = rememberPhotoPicker { result ->
        when (result) {
            is PhotoPickResult.Picked -> {
                // Gate on !processing && !isCapturing: while a photo is already being persisted,
                // a duplicate picker result must not start a second resize+save (the VM has the
                // same guard; this one also skips the wasted resize work).
                if (!processing && !state.isCapturing) {
                    awaitingChoice = false
                    lastPickFailed = false
                    processing = true
                    scope.launch {
                        // Resize is CPU-bound and can take hundreds of ms on a 12MP shot.
                        // Hop off the main thread so the processing overlay below actually gets a
                        // frame instead of the screen freezing. Dispatchers.Default directly (not
                        // DispatcherProvider) because this is presentation-layer image work, not a
                        // data-layer IO boundary — the one-withContext-per-repository-method rule
                        // governs repositories, and Default exists on every KMP target.
                        val bytes = withContext(Dispatchers.Default) {
                            result.bytes.resizeForUpload()
                        }
                        vm.onIntent(CaptureMealIntent.PhotoTaken(bytes, result.source, result.metadata))
                    }
                }
            }
            is PhotoPickResult.PickedMultiple -> {
                // Defensive only — CaptureMealScreen always launches single-pick (`launchCamera()`
                // / the no-arg `launchGallery()`), so this arm should be unreachable; Wave 3 owns
                // the real multi-photo capture UX. Treat the first photo exactly like a single
                // Picked (same guard/overlay/resize handling as that arm, duplicated rather than
                // shared so this stays a minimal, obviously-temporary scaffold).
                val first = result.photos.first()
                if (!processing && !state.isCapturing) {
                    awaitingChoice = false
                    lastPickFailed = false
                    processing = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            first.bytes.resizeForUpload()
                        }
                        vm.onIntent(CaptureMealIntent.PhotoTaken(bytes, first.source, first.metadata))
                    }
                }
            }
            PhotoPickResult.Cancelled -> { lastPickFailed = false; awaitingChoice = true }
            is PhotoPickResult.Failed -> { lastPickFailed = true; awaitingChoice = true }
        }
    }

    LaunchedEffect(Unit) {
        vm.onIntent(CaptureMealIntent.Start)
        picker.launchCamera()
    }

    // BUG FIX (pre-existing, review L2): `processing` flips true on the first Picked result and
    // was never reset, so a later SetPhoto failure (state.error set, isCapturing back to false)
    // left the "Saving your plate…" overlay up FOREVER, masking the error banner branch below.
    // Reset it the moment the VM surfaces an error so the banner can actually win. The success
    // path needs no reset — NavigateToCompose leaves the screen.
    LaunchedEffect(state.error) {
        if (state.error != null) processing = false
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                CaptureMealEffect.OpenAppSettings   -> onOpenSettings()
            }
        }
    }

    // Between the camera handing the shot back and NavigateToCompose firing, the photo is
    // resized + persisted into the draft. Without feedback the screen read as frozen and
    // invited re-taps — cover the wait with a structural field floor + progress indicator.
    // `processing` flips synchronously in the picker callback so the overlay is already up
    // during the resize step (before the VM flips isCapturing).
    val processingPhoto = processing || state.isCapturing
    if (processingPhoto) {
        Box(modifier = Modifier.fillMaxSize()) {
            FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, tone = FrFloorTone.Adaptive)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    // Announce the wait to TalkBack/VoiceOver when the overlay appears.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FrProgressIndicator()
                FrText(
                    text = resolve(MealStringKey.CaptureSavingPlate),
                    style = StructuralType.body,
                    color = StructuralColors.foreground,
                )
            }
        }
    } else if (awaitingChoice) {
        // The camera was dismissed without a photo. Offer a retry alongside the gallery pick —
        // the entry point for choosing a plate photo from the device's library — instead of
        // silently bouncing the user out via onCancelled.
        Box(modifier = Modifier.fillMaxSize()) {
            FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, tone = FrFloorTone.Adaptive)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    // Announce the fallback choice to TalkBack/VoiceOver as soon as it appears.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FrText(
                    text = resolve(
                        if (lastPickFailed) MealStringKey.CaptureFailedTitle
                        else MealStringKey.CaptureFallbackTitle,
                    ),
                    style = StructuralType.titleMd,
                    color = StructuralColors.foreground,
                )
                FrGlassButton(
                    label = resolve(MealStringKey.CaptureRetryCamera),
                    onClick = { awaitingChoice = false; picker.launchCamera() },
                    tone = FrButtonTone.Primary,
                    fillWidth = true,
                )
                FrGlassButton(
                    label = resolve(MealStringKey.CaptureChooseFromGallery),
                    onClick = { awaitingChoice = false; picker.launchGallery() },
                    tone = FrButtonTone.Glass,
                    leadingIcon = FrIcons.GalleryImport,
                    fillWidth = true,
                )
                FrGlassButton(
                    label = resolve(CommonStringKey.Cancel),
                    onClick = onCancelled,
                    tone = FrButtonTone.Ghost,
                    fillWidth = true,
                )
            }
        }
    } else {
        // The screen is otherwise a transparent camera launcher; when starting the
        // draft or saving the photo fails, surface the reason as a banner so the
        // failure isn't silent (the user can back out via the camera dismiss path).
        state.error?.let { error ->
            FrScreenScaffold {
                FrErrorBanner(
                    text = resolve(error),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}
