package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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

// Locally-defined AddAPhoto vector. material-icons-extended (which ships the
// real `Icons.Filled.AddAPhoto`) has no KMP-compatible iOS artifact, so we
// replicate the path data here using the `materialIcon` DSL. The path is the
// standard Material Design "add_a_photo" filled glyph: a "+" badge in the
// top-left, a camera body with a lens cut-out (EvenOdd fill), plus a small
// solid dot for the lens center.
private val AddAPhotoVector: ImageVector = materialIcon(name = "Filled.AddAPhoto") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        // "+" badge in the top-left corner.
        moveTo(3f, 4f)
        verticalLineTo(1f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(2f)
        horizontalLineTo(5f)
        verticalLineToRelative(3f)
        horizontalLineTo(3f)
        verticalLineTo(6f)
        horizontalLineTo(0f)
        verticalLineTo(4f)
        horizontalLineTo(3f)
        close()
        // Camera body with the lens hole (EvenOdd cuts the inner circle).
        moveTo(6f, 10f)
        verticalLineTo(7f)
        horizontalLineToRelative(3f)
        verticalLineTo(4f)
        horizontalLineToRelative(7f)
        lineToRelative(1.83f, 2f)
        horizontalLineTo(21f)
        curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
        verticalLineTo(10f)
        horizontalLineTo(6f)
        close()
        moveTo(13f, 19f)
        curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
        reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
        reflectiveCurveToRelative(-5f, 2.24f, -5f, 5f)
        reflectiveCurveTo(10.24f, 19f, 13f, 19f)
        close()
    }
    materialPath {
        // Solid lens center.
        moveTo(9.8f, 14f)
        curveToRelative(0f, 1.77f, 1.43f, 3.2f, 3.2f, 3.2f)
        reflectiveCurveToRelative(3.2f, -1.43f, 3.2f, -3.2f)
        reflectiveCurveToRelative(-1.43f, -3.2f, -3.2f, -3.2f)
        reflectiveCurveTo(9.8f, 12.23f, 9.8f, 14f)
        close()
    }
}

// Locally-defined Image vector. material-icons-extended (which ships the real
// `Icons.Filled.Image`) has no KMP-compatible iOS artifact. Path is the
// standard Material Design "image" filled glyph: a rounded square frame with
// a stylized "mountain peak" cut-out (EvenOdd fill).
private val ImageFilledVector: ImageVector = materialIcon(name = "Filled.Image") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(21f, 19f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        close()
        moveTo(8.5f, 13.5f)
        lineToRelative(2.5f, 3.01f)
        lineTo(14.5f, 12f)
        lineToRelative(4.5f, 6f)
        horizontalLineTo(5f)
        lineToRelative(3.5f, -4.5f)
        close()
    }
}

// Locally-defined NoPhotography vector. material-icons-extended (which ships
// the real `Icons.Filled.NoPhotography`) has no KMP-compatible iOS artifact.
// Path is the standard Material Design "no_photography" filled glyph: a
// camera body with a diagonal strike-through and a partial lens.
private val NoPhotographyVector: ImageVector = materialIcon(name = "Filled.NoPhotography") {
    materialPath {
        // Upper-right camera body fragment.
        moveTo(10.94f, 8.12f)
        lineTo(7.48f, 4.66f)
        lineTo(9f, 3f)
        horizontalLineToRelative(6f)
        lineToRelative(1.83f, 2f)
        horizontalLineTo(20f)
        curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 0.05f, -0.01f, 0.1f, -0.02f, 0.16f)
        lineToRelative(-5.1f, -5.1f)
        curveTo(16.96f, 13.71f, 17f, 13.36f, 17f, 13f)
        curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
        curveTo(11.64f, 8f, 11.29f, 8.04f, 10.94f, 8.12f)
        close()
        // Lower-left camera body fragment + diagonal strike (the "no" slash).
        moveTo(20.49f, 23.31f)
        lineTo(18.17f, 21f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
        verticalLineTo(7f)
        curveToRelative(0f, -0.59f, 0.27f, -1.12f, 0.68f, -1.49f)
        lineToRelative(-2f, -2f)
        lineTo(2.1f, 2.1f)
        lineToRelative(19.8f, 19.8f)
        lineTo(20.49f, 23.31f)
        close()
        // Partial lens.
        moveTo(14.49f, 17.32f)
        lineToRelative(-1.5f, -1.5f)
        curveTo(12.67f, 15.92f, 12.35f, 16f, 12f, 16f)
        curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
        curveToRelative(0f, -0.35f, 0.08f, -0.67f, 0.19f, -0.98f)
        lineToRelative(-1.5f, -1.5f)
        curveTo(7.25f, 11.24f, 7f, 12.09f, 7f, 13f)
        curveToRelative(0f, 2.76f, 2.24f, 5f, 5f, 5f)
        curveTo(12.91f, 18f, 13.76f, 17.75f, 14.49f, 17.32f)
        close()
    }
}

// Locally-defined Crown vector. material-icons-extended (`Icons.Filled.EmojiEvents`)
// has no KMP-compatible iOS artifact. Path is a simplified crown glyph for the
// "best plate" podium badge in stats.
private val CrownVector: ImageVector = materialIcon(name = "Filled.Crown") {
    materialPath {
        // Crown body: three pointed peaks rising from a base bar.
        moveTo(5f, 16f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(3f)
        horizontalLineTo(5f)
        close()
        moveTo(12f, 4f)
        lineTo(15f, 10f)
        lineTo(19f, 6f)
        lineTo(19f, 14f)
        horizontalLineTo(5f)
        lineTo(5f, 6f)
        lineTo(9f, 10f)
        close()
    }
}

// Locally-defined Flame vector. material-icons-extended (`Icons.Filled.Whatshot`)
// has no KMP-compatible iOS artifact. Path is a stylized flame for the streak hero.
private val FlameVector: ImageVector = materialIcon(name = "Filled.Flame") {
    materialPath {
        moveTo(13.5f, 0.67f)
        curveToRelative(0f, 0f, 0.74f, 2.65f, 0.74f, 4.8f)
        curveToRelative(0f, 2.06f, -1.35f, 3.73f, -3.41f, 3.73f)
        curveToRelative(-2.07f, 0f, -3.63f, -1.67f, -3.63f, -3.73f)
        lineToRelative(0.03f, -0.36f)
        curveTo(5.21f, 7.51f, 3f, 10.62f, 3f, 14.25f)
        curveTo(3f, 18.81f, 6.69f, 22.5f, 11.25f, 22.5f)
        curveToRelative(4.56f, 0f, 8.25f, -3.69f, 8.25f, -8.25f)
        curveTo(19.5f, 8.74f, 16.79f, 3.91f, 13.5f, 0.67f)
        close()
        moveTo(11.71f, 19f)
        curveToRelative(-1.78f, 0f, -3.22f, -1.4f, -3.22f, -3.14f)
        curveToRelative(0f, -1.62f, 1.05f, -2.76f, 2.81f, -3.12f)
        curveToRelative(1.77f, -0.36f, 3.6f, -1.21f, 4.62f, -2.58f)
        curveToRelative(0.39f, 1.29f, 0.59f, 2.65f, 0.59f, 4.04f)
        curveTo(16.5f, 16.81f, 14.36f, 19f, 11.71f, 19f)
        close()
    }
}

// Locally-defined BarChart vector. material-icons-extended (which ships the
// real `Icons.Filled.BarChart`) has no KMP-compatible iOS artifact. Path is
// the standard Material Design "bar_chart" filled glyph: three solid bars of
// varying heights aligned along a common baseline at y=20.
private val BarChartVector: ImageVector = materialIcon(name = "Filled.BarChart") {
    materialPath {
        // Left bar: rect x=4 y=9 w=4 h=11 → (4,9)-(8,20).
        moveTo(4f, 9f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(11f)
        horizontalLineTo(4f)
        close()
        // Center (tallest) bar: rect x=10 y=4 w=4 h=16 → (10,4)-(14,20).
        moveTo(10f, 4f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(16f)
        horizontalLineTo(10f)
        close()
        // Right bar: rect x=16 y=13 w=4 h=7 → (16,13)-(20,20).
        moveTo(16f, 13f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(7f)
        horizontalLineTo(16f)
        close()
    }
}

object FrIcons {
    val Back: ImageVector          = Icons.Filled.ArrowBack
    val Camera: ImageVector        = PhotoCameraVector
    val AddPhoto: ImageVector      = AddAPhotoVector
    val GalleryImport: ImageVector = ImageFilledVector
    val CameraOff: ImageVector     = NoPhotographyVector
    val Settings: ImageVector      = Icons.Filled.Settings
    val Close: ImageVector         = Icons.Filled.Close
    // Bottom-nav tabs.
    val Home: ImageVector          = Icons.Filled.Home
    val Stats: ImageVector         = BarChartVector
    val Crown: ImageVector         = CrownVector
    val Flame: ImageVector         = FlameVector
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
        "AddPhoto"      to FrIcons.AddPhoto,
        "Gallery"       to FrIcons.GalleryImport,
        "CameraOff"     to FrIcons.CameraOff,
        "Settings"      to FrIcons.Settings,
        "Close"         to FrIcons.Close,
        "Home"          to FrIcons.Home,
        "Stats"         to FrIcons.Stats,
        "Crown"         to FrIcons.Crown,
        "Flame"         to FrIcons.Flame,
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
        }
    }
}
