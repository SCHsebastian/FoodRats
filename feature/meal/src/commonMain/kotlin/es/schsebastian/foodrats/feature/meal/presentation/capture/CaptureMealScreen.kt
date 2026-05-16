package es.schsebastian.foodrats.feature.meal.presentation.capture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.preat.peekaboo.ui.camera.CameraMode
import com.preat.peekaboo.ui.camera.PeekabooCamera
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrShutterButton
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrCaptureLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CaptureMealScreen(
    onCaptured: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: CaptureMealViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onIntent(CaptureMealIntent.Start) }
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CaptureMealEffect.NavigateToCompose -> onCaptured()
                CaptureMealEffect.OpenAppSettings    -> onOpenSettings()
                CaptureMealEffect.OpenGalleryPicker -> { /* deferred */ }
            }
        }
    }
    FrScreenScaffold {
        FrCaptureLayout(
            viewfinder = {
                PeekabooCamera(
                    modifier = Modifier.fillMaxSize(),
                    cameraMode = CameraMode.Back,
                    captureIcon = { onClick -> FrShutterButton(onClick = onClick, enabled = !state.isCapturing) },
                    onCapture = { bytes -> bytes?.let { vm.onIntent(CaptureMealIntent.PhotoTaken(it)) } },
                    permissionDeniedContent = {
                        FrEmptyState(
                            icon = FrIcons.CameraOff,
                            headline = resolve(MealStringKey.CaptureNoPermissionHeadline),
                            subtext = resolve(MealStringKey.CaptureNoPermissionSubtext),
                            cta = {
                                FrButton(
                                    label = resolve(CommonStringKey.OpenSettings),
                                    onClick = { vm.onIntent(CaptureMealIntent.OpenSettings) },
                                    variant = FrButtonVariant.Primary,
                                )
                            },
                        )
                    },
                )
            },
            controls = {
                FrIconButton(icon = FrIcons.GalleryImport, onClick = { vm.onIntent(CaptureMealIntent.PickFromGallery) })
            },
        )
    }
    state.error?.let { err -> FrErrorBanner(text = resolve(err.toStringKey())) }
}
