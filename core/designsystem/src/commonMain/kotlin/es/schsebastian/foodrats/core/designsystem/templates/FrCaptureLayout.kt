package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShutterButton
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewSurface
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrCaptureLayout(
    viewfinder: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        viewfinder()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Spacing.md),
            contentAlignment = Alignment.BottomCenter,
        ) {
            controls()
        }
    }
}

@Composable
private fun FrCaptureLayoutSample(darkTheme: Boolean) {
    FrPreviewSurface(label = if (darkTheme) "Dark" else "Light", darkTheme = darkTheme, padding = PaddingValues(0.dp)) {
        FrCaptureLayout(
            modifier = Modifier.height(420.dp).fillMaxWidth(),
            viewfinder = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Viewfinder", style = MaterialTheme.typography.titleMedium)
                }
            },
            controls = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FrIconButton(icon = FrIcons.GalleryImport, onClick = {}, contentDescription = "Gallery")
                    FrShutterButton(onClick = {}, contentDescription = "Take photo")
                    FrIconButton(icon = FrIcons.Settings, onClick = {}, contentDescription = "Settings")
                }
            },
        )
    }
}

@FrPreview
@Composable
private fun FrCaptureLayoutLightPreview() = FrCaptureLayoutSample(darkTheme = false)

@FrPreview
@Composable
private fun FrCaptureLayoutDarkPreview() = FrCaptureLayoutSample(darkTheme = true)
