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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

// Locally-defined PhotoCamera vector. material-icons-extended (which ships the real
// `Icons.Filled.PhotoCamera`) has no KMP-compatible iOS artifact, so we replicate
// the path data here using the `materialIcon` DSL from material-icons-core. The
// path is the standard Material Design "photo_camera" filled glyph: an outer
// rounded camera body with the lens cut out via even-odd fill, plus a small solid
// dot for the lens center.
private val PhotoCameraVector: ImageVector = materialIcon(name = "Filled.PhotoCamera") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(9f, 2f)
        lineTo(7.17f, 4f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(6f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineToRelative(-3.17f)
        lineTo(15f, 2f)
        horizontalLineTo(9f)
        close()
        moveTo(12f, 17f)
        curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
        reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
        reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
        reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
        close()
    }
    materialPath {
        moveTo(12f, 9f)
        curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
        reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
        reflectiveCurveToRelative(3f, -1.34f, 3f, -3f)
        reflectiveCurveTo(13.66f, 9f, 12f, 9f)
        close()
    }
}

// NOTE: AddAPhoto, Image (GalleryImport) and NoPhotography are still in
// material-icons-extended (no KMP iOS artifact). We substitute with
// material-icons-core equivalents until those icons can be vendored too.
object FrIcons {
    val Back: ImageVector          = Icons.Filled.ArrowBack
    val Camera: ImageVector        = PhotoCameraVector
    val AddPhoto: ImageVector      = Icons.Filled.Add        // placeholder for AddAPhoto
    val GalleryImport: ImageVector = Icons.Filled.List       // placeholder for Image
    val CameraOff: ImageVector     = Icons.Filled.Warning    // placeholder for NoPhotography
    val Settings: ImageVector      = Icons.Filled.Settings
    val Close: ImageVector         = Icons.Filled.Close
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
        "Camera"        to FrIcons.Camera,
        "AddPhoto*"     to FrIcons.AddPhoto,
        "Gallery*"      to FrIcons.GalleryImport,
        "CameraOff*"    to FrIcons.CameraOff,
        "Settings"      to FrIcons.Settings,
        "Close"         to FrIcons.Close,
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
