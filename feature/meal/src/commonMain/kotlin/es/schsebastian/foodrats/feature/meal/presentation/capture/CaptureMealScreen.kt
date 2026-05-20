package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                println("[CaptureMealScreen] picker error: ${r.exception.message}")
                picker.reset()
                onCancelled()
            }
            is ImagePickerResult.Loading, is ImagePickerResult.Idle -> Unit
        }
    }
}
