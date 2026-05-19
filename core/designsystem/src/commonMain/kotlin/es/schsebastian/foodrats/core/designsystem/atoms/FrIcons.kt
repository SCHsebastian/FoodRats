package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

// NOTE: Camera-related icons (AddAPhoto, Camera, Image, NoPhotography) are in
// material-icons-extended which does not publish a KMP-compatible artifact for iOS.
// We substitute with material-icons-core equivalents for the multiplatform scaffold;
// replace with platform-specific icon packs when the capture feature is hardened.
object FrIcons {
    val Back: ImageVector          = Icons.Filled.ArrowBack
    val Camera: ImageVector        = Icons.Filled.Build      // placeholder for Camera
    val AddPhoto: ImageVector      = Icons.Filled.Add        // placeholder for AddAPhoto
    val GalleryImport: ImageVector = Icons.Filled.List       // placeholder for Image
    val CameraOff: ImageVector     = Icons.Filled.Warning    // placeholder for NoPhotography
    val Settings: ImageVector      = Icons.Filled.Settings
    // Bottom-nav tabs.
    val Home: ImageVector          = Icons.Filled.Home
    val Stats: ImageVector         = Icons.Filled.Star       // placeholder for BarChart
    // ChevronLeft/Right are in material-icons-extended (no KMP iOS artifact); use
    // KeyboardArrowLeft/Right from material-icons-core automirrored as substitutes.
    val ChevronLeft: ImageVector   = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val ChevronRight: ImageVector  = Icons.AutoMirrored.Filled.KeyboardArrowRight
}

@FrPreview
@Composable
private fun FrIconsPreview() {
    val entries: List<Pair<String, ImageVector>> = listOf(
        "Back"          to FrIcons.Back,
        "Camera*"       to FrIcons.Camera,
        "AddPhoto*"     to FrIcons.AddPhoto,
        "Gallery*"      to FrIcons.GalleryImport,
        "CameraOff*"    to FrIcons.CameraOff,
        "Settings"      to FrIcons.Settings,
        "Home"          to FrIcons.Home,
        "Stats*"        to FrIcons.Stats,
        "ChevronLeft"   to FrIcons.ChevronLeft,
        "ChevronRight"  to FrIcons.ChevronRight,
    )
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.chunked(5).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { (label, icon) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FrIcon(image = icon, modifier = Modifier.size(Sizes.iconMd), contentDescription = label)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(
                "* = material-icons-extended substitute (no KMP iOS artifact)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
