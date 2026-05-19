package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrCaptureLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.time.SystemClock
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.presentation.components.CaptureFrame
import es.schsebastian.foodrats.feature.meal.presentation.components.DailyEmoteBadge
import es.schsebastian.foodrats.feature.meal.presentation.components.resizeForUpload
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.domain.models.MimeType
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.datetime.TimeZone
import kotlinx.io.readByteArray
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CaptureMealScreen(
    onCaptured: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CaptureMealViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberImagePickerKMP()
    val today = remember { MealDay.today(SystemClock(), TimeZone.currentSystemDefault()) }
    val emote = remember(today) { DailyEmote.forDay(today) }

    LaunchedEffect(Unit) { vm.onIntent(CaptureMealIntent.Start) }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                CaptureMealEffect.OpenAppSettings   -> onOpenSettings()
                CaptureMealEffect.OpenGalleryPicker -> picker.launchGallery(
                    allowMultiple = false,
                    mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG),
                )
            }
        }
    }

    LaunchedEffect(picker.result) {
        when (val result = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = result.first ?: return@LaunchedEffect
                // Resize + re-encode at JPEG 75% / max 1280px BEFORE handing bytes
                // off — keeps Storage costs sane and uploads fast on mobile data.
                val bytes = photo.asSource().readByteArray().resizeForUpload()
                vm.onIntent(CaptureMealIntent.PhotoTaken(bytes))
                picker.reset()
            }
            is ImagePickerResult.Error -> {
                println("[CaptureMealScreen] picker error: ${result.exception.message}")
                picker.reset()
            }
            is ImagePickerResult.Dismissed,
            is ImagePickerResult.Loading,
            is ImagePickerResult.Idle -> Unit
        }
    }

    FrScreenScaffold {
        Box(modifier = Modifier.fillMaxSize()) {
            FrCaptureLayout(
                viewfinder = {
                    CaptureFrame(onTap = { picker.launchCamera() })
                },
                controls = {
                    FrIconButton(icon = FrIcons.GalleryImport, onClick = { vm.onIntent(CaptureMealIntent.PickFromGallery) })
                },
            )
            DailyEmoteBadge(
                emote = emote,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(Spacing.sm),
            )
        }
    }
    state.error?.let { err -> FrErrorBanner(text = resolve(err.toStringKey())) }
}
