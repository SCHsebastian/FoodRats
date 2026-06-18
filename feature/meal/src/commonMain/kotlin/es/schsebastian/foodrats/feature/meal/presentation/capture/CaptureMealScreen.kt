package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.presentation.components.resizeForUpload
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
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
                val bytes = photo.asSource().readByteArray().resizeForUpload()
                vm.onIntent(CaptureMealIntent.PhotoTaken(bytes))
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
