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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.structural.FrFloorTone
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.resizeForUpload
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CaptureMealScreen(
    onCaptured: () -> Unit,
    onCancelled: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CaptureMealViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberImagePickerKMP()

    LaunchedEffect(Unit) {
        vm.onIntent(CaptureMealIntent.Start)
        picker.launchCamera()
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                CaptureMealEffect.OpenAppSettings   -> onOpenSettings()
            }
        }
    }

    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = r.first ?: return@LaunchedEffect
                // Gate on !isCapturing: while a photo is already being persisted, a duplicate
                // picker result must not start a second resize+save (the VM has the same guard;
                // this one also skips the wasted resize work).
                if (!state.isCapturing) {
                    // Read + resize is CPU-bound and can take hundreds of ms on a 12MP shot.
                    // Hop off the main thread so the isCapturing overlay below actually gets a
                    // frame instead of the screen freezing. Dispatchers.Default directly (not
                    // DispatcherProvider) because this is presentation-layer image work, not a
                    // data-layer IO boundary — the one-withContext-per-repository-method rule
                    // governs repositories, and Default exists on every KMP target.
                    val bytes = withContext(Dispatchers.Default) {
                        photo.asSource().readByteArray().resizeForUpload()
                    }
                    vm.onIntent(CaptureMealIntent.PhotoTaken(bytes))
                }
                picker.reset()
            }
            is ImagePickerResult.Dismissed -> {
                picker.reset()
                onCancelled()
            }
            is ImagePickerResult.Error -> {
                picker.reset()
                onCancelled()
            }
            is ImagePickerResult.Loading, is ImagePickerResult.Idle -> Unit
        }
    }

    // Between the camera handing the shot back and NavigateToCompose firing, the photo is
    // resized + persisted into the draft. Without feedback the screen read as frozen and
    // invited re-taps — cover the wait with a structural field floor + progress indicator.
    // picker.result is part of the condition so the overlay is already up during the resize
    // step (before the VM flips isCapturing).
    val processingPhoto = state.isCapturing || picker.result is ImagePickerResult.Success
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
