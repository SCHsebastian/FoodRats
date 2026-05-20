package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewSurface

@Composable
fun FrScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    // safeDrawing covers status bar + navigation bar + display cutout + IME so
    // standalone screens (no outer Scaffold) don't draw under system bars.
    // When nested inside another Scaffold that already consumes insets (e.g.
    // MainScaffold), pass WindowInsets(0) to avoid double-padding.
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentWindowInsets,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun FrScreenScaffoldSample(darkTheme: Boolean) {
    FrPreviewSurface(label = if (darkTheme) "Dark" else "Light", darkTheme = darkTheme, padding = PaddingValues(0.dp)) {
        FrScreenScaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("FoodRats") })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { FrIcon(image = FrIcons.Home, contentDescription = "Home") },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {},
                        icon = { FrIcon(image = FrIcons.Stats, contentDescription = "Stats") },
                        label = { Text("Stats") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {},
                        icon = { FrIcon(image = FrIcons.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Screen body", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@FrPreview
@Composable
private fun FrScreenScaffoldLightPreview() = FrScreenScaffoldSample(darkTheme = false)

@FrPreview
@Composable
private fun FrScreenScaffoldDarkPreview() = FrScreenScaffoldSample(darkTheme = true)
