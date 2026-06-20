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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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

// Vendored Warning glyph — material-icons-core has no Filled.Warning. Standard
// MD "warning" filled triangle with exclamation mark cut out (EvenOdd).
private val WarningVector: ImageVector = materialIcon(name = "Filled.Warning") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(1f, 21f)
        horizontalLineTo(23f)
        lineTo(12f, 2f)
        lineTo(1f, 21f)
        close()
        moveTo(13f, 18f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(2f)
        close()
        moveTo(13f, 14f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(4f)
        close()
    }
}

// Vendored Flag glyph (report action) — material-icons-core has no Filled.Flag.
private val FlagVector: ImageVector = materialIcon(name = "Filled.Flag") {
    materialPath {
        moveTo(14.4f, 6f)
        lineToRelative(-0.24f, -1.2f)
        curveToRelative(-0.09f, -0.46f, -0.5f, -0.8f, -0.98f, -0.8f)
        horizontalLineTo(6f)
        curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
        verticalLineToRelative(15f)
        curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
        reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
        verticalLineToRelative(-6f)
        horizontalLineToRelative(5.6f)
        lineToRelative(0.24f, 1.2f)
        curveToRelative(0.09f, 0.47f, 0.5f, 0.8f, 0.98f, 0.8f)
        horizontalLineTo(19f)
        curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
        verticalLineTo(7f)
        curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
        horizontalLineToRelative(-4.6f)
        close()
    }
}

// Vendored Block (no-entry) glyph — material-icons-core has no Filled.Block.
private val BlockVector: ImageVector = materialIcon(name = "Filled.Block") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(4f, 12f)
        curveToRelative(0f, -4.42f, 3.58f, -8f, 8f, -8f)
        curveToRelative(1.85f, 0f, 3.55f, 0.63f, 4.9f, 1.69f)
        lineTo(5.69f, 16.9f)
        curveTo(4.63f, 15.55f, 4f, 13.85f, 4f, 12f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-1.85f, 0f, -3.55f, -0.63f, -4.9f, -1.69f)
        lineTo(18.31f, 7.1f)
        curveTo(19.37f, 8.45f, 20f, 10.15f, 20f, 12f)
        curveToRelative(0f, 4.42f, -3.58f, 8f, -8f, 8f)
        close()
    }
}

// Vendored Delete (trash can) glyph — material-icons-core has no Filled.Delete.
private val DeleteVector: ImageVector = materialIcon(name = "Filled.Delete") {
    materialPath {
        moveTo(6f, 19f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(8f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(7f)
        horizontalLineTo(6f)
        verticalLineToRelative(12f)
        close()
        moveTo(19f, 4f)
        horizontalLineToRelative(-3.5f)
        lineToRelative(-1f, -1f)
        horizontalLineToRelative(-5f)
        lineToRelative(-1f, 1f)
        horizontalLineTo(5f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(14f)
        verticalLineTo(4f)
        close()
    }
}

// Vendored ChatBubble glyph — material-icons-core has no Filled.ChatBubble. Standard
// MD "chat_bubble" filled rounded-rectangle speech bubble with a tail at bottom-left.
private val CommentVector: ImageVector = materialIcon(name = "Filled.Comment") {
    materialPath {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(18f)
        lineToRelative(4f, -4f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
    }
}

// Vendored Group (people) glyph — material-icons-core has no Filled.Group. Standard
// MD "group" filled glyph: two overlapping head+shoulders silhouettes. Used as the
// crew/membership mark (e.g. the "no active crew" empty state).
private val GroupVector: ImageVector = materialIcon(name = "Filled.Group") {
    materialPath {
        moveTo(16f, 11f)
        curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
        reflectiveCurveTo(17.66f, 5f, 16f, 5f)
        curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
        reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
        close()
        moveTo(8f, 11f)
        curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
        reflectiveCurveTo(9.66f, 5f, 8f, 5f)
        curveTo(6.34f, 5f, 5f, 6.34f, 5f, 8f)
        reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
        close()
        moveTo(8f, 13f)
        curveToRelative(-2.33f, 0f, -7f, 1.17f, -7f, 3.5f)
        verticalLineTo(19f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2.5f)
        curveToRelative(0f, -2.33f, -4.67f, -3.5f, -7f, -3.5f)
        close()
        moveTo(16f, 13f)
        curveToRelative(-0.29f, 0f, -0.62f, 0.02f, -0.97f, 0.05f)
        curveToRelative(1.16f, 0.84f, 1.97f, 1.97f, 1.97f, 3.45f)
        verticalLineTo(19f)
        horizontalLineToRelative(6f)
        verticalLineToRelative(-2.5f)
        curveToRelative(0f, -2.33f, -4.67f, -3.5f, -7f, -3.5f)
        close()
    }
}

// Material `place` (location pin) glyph — vendored because material-icons-extended
// has no KMP iOS artifact. Standard 24x24 teardrop with a circle hole.
private val PlaceVector: ImageVector = materialIcon(name = "Filled.Place") {
    materialPath {
        moveTo(12f, 2f)
        curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
        curveToRelative(0f, 5.25f, 7f, 13f, 7f, 13f)
        reflectiveCurveToRelative(7f, -7.75f, 7f, -13f)
        curveToRelative(0f, -3.87f, -3.13f, -7f, -7f, -7f)
        close()
        moveTo(12f, 11.5f)
        curveToRelative(-1.38f, 0f, -2.5f, -1.12f, -2.5f, -2.5f)
        reflectiveCurveToRelative(1.12f, -2.5f, 2.5f, -2.5f)
        reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f)
        reflectiveCurveToRelative(-1.12f, 2.5f, -2.5f, 2.5f)
        close()
    }
}

// Vendored Person glyph — material-icons-core has no Filled.Person. Standard MD
// "person" filled glyph: head circle over a shoulders arc. Used for the account row.
private val PersonVector: ImageVector = materialIcon(name = "Filled.Person") {
    materialPath {
        moveTo(12f, 12f)
        curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
        reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
        reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
        reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
        close()
        moveTo(12f, 14f)
        curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(-2f)
        curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
        close()
    }
}

// Vendored DarkMode (crescent moon) glyph — used for the theme row.
private val DarkModeVector: ImageVector = materialIcon(name = "Filled.DarkMode") {
    materialPath {
        moveTo(12f, 3f)
        curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
        reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
        reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
        curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
        curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
        curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
        curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
        curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
        close()
    }
}

// Vendored Language (globe) glyph — used for the language row.
private val LanguageVector: ImageVector = materialIcon(name = "Filled.Language") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(11.99f, 2f)
        curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
        close()
        moveTo(18.92f, 8f)
        horizontalLineToRelative(-2.95f)
        curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
        curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
        close()
        moveTo(12f, 4.04f)
        curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
        horizontalLineToRelative(-3.82f)
        curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
        close()
        moveTo(4.26f, 14f)
        curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
        reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2f)
        horizontalLineToRelative(3.38f)
        curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
        curveToRelative(0f, 0.68f, 0.06f, 1.34f, 0.14f, 2f)
        horizontalLineTo(4.26f)
        close()
        moveTo(5.08f, 16f)
        horizontalLineToRelative(2.95f)
        curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
        curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
        close()
        moveTo(8.03f, 8f)
        horizontalLineTo(5.08f)
        curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
        curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
        close()
        moveTo(12f, 19.96f)
        curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
        horizontalLineToRelative(3.82f)
        curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
        close()
        moveTo(14.34f, 14f)
        horizontalLineTo(9.66f)
        curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2f)
        curveToRelative(0f, -0.68f, 0.07f, -1.35f, 0.16f, -2f)
        horizontalLineToRelative(4.68f)
        curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
        curveToRelative(0f, 0.68f, -0.07f, 1.34f, -0.16f, 2f)
        close()
        moveTo(14.59f, 19.56f)
        curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
        horizontalLineToRelative(2.95f)
        curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
        close()
        moveTo(16.36f, 14f)
        curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2f)
        curveToRelative(0f, -0.68f, -0.06f, -1.34f, -0.14f, -2f)
        horizontalLineToRelative(3.38f)
        curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2f)
        reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2f)
        horizontalLineToRelative(-3.38f)
        close()
    }
}

// Vendored Notifications (bell) glyph — used for the notifications row.
private val NotificationsVector: ImageVector = materialIcon(name = "Filled.Notifications") {
    materialPath {
        moveTo(12f, 22f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        horizontalLineToRelative(-4f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        close()
        moveTo(18f, 16f)
        verticalLineToRelative(-5f)
        curveToRelative(0f, -3.07f, -1.64f, -5.64f, -4.5f, -6.32f)
        verticalLineTo(4f)
        curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
        reflectiveCurveToRelative(-1.5f, 0.67f, -1.5f, 1.5f)
        verticalLineToRelative(0.68f)
        curveTo(7.63f, 5.36f, 6f, 7.92f, 6f, 11f)
        verticalLineToRelative(5f)
        lineToRelative(-2f, 2f)
        verticalLineToRelative(1f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(-1f)
        lineToRelative(-2f, -2f)
        close()
    }
}

// Vendored Logout glyph — used for the sign-out row (Back arrow was semantically wrong).
private val LogoutVector: ImageVector = materialIcon(name = "Filled.Logout") {
    materialPath {
        moveTo(17f, 7f)
        lineToRelative(-1.41f, 1.41f)
        lineTo(18.17f, 11f)
        horizontalLineTo(8f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(10.17f)
        lineToRelative(-2.58f, 2.58f)
        lineTo(17f, 17f)
        lineToRelative(5f, -5f)
        close()
        moveTo(4f, 5f)
        horizontalLineToRelative(8f)
        verticalLineTo(3f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineTo(5f)
        close()
    }
}

// ── Achievement-badge glyphs (vendored; material-icons-extended has no iOS artifact) ──

// Restaurant (fork + knife) — the "plate / meal" achievement family.
private val RestaurantVector: ImageVector = materialIcon(name = "Filled.Restaurant") {
    materialPath {
        moveTo(11f, 9f)
        horizontalLineTo(9f)
        verticalLineTo(2f)
        horizontalLineTo(7f)
        verticalLineToRelative(7f)
        horizontalLineTo(5f)
        verticalLineTo(2f)
        horizontalLineTo(3f)
        verticalLineToRelative(7f)
        curveToRelative(0f, 2.12f, 1.66f, 3.84f, 3.75f, 3.97f)
        verticalLineTo(22f)
        horizontalLineToRelative(2.5f)
        verticalLineToRelative(-9.03f)
        curveTo(11.34f, 12.84f, 13f, 11.12f, 13f, 9f)
        verticalLineTo(2f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(7f)
        close()
        moveTo(16f, 6f)
        verticalLineToRelative(8f)
        horizontalLineToRelative(2.5f)
        verticalLineToRelative(8f)
        horizontalLineTo(21f)
        verticalLineTo(2f)
        curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 4f)
        close()
    }
}

// EmojiEvents-style trophy — the generic achievement / award glyph.
private val TrophyVector: ImageVector = materialIcon(name = "Filled.Trophy") {
    materialPath {
        moveTo(19f, 5f)
        horizontalLineToRelative(-2f)
        verticalLineTo(3f)
        horizontalLineTo(7f)
        verticalLineToRelative(2f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(1f)
        curveToRelative(0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
        curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
        verticalLineTo(19f)
        horizontalLineTo(7f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(10f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(-3.1f)
        curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
        curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
        verticalLineTo(7f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(5f, 8f)
        verticalLineTo(7f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(3.82f)
        curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
        close()
        moveTo(19f, 8f)
        curveToRelative(0f, 1.3f, -0.84f, 2.4f, -2f, 2.82f)
        verticalLineTo(7f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(1f)
        close()
    }
}

// Eco (leaf) — the "ingredient variety" achievement family.
private val EcoVector: ImageVector = materialIcon(name = "Filled.Eco") {
    materialPath {
        moveTo(6.05f, 8.05f)
        curveToRelative(-2.73f, 2.73f, -2.73f, 7.15f, -0.02f, 9.88f)
        curveToRelative(1.47f, -3.4f, 4.09f, -6.24f, 7.36f, -7.93f)
        curveToRelative(-2.77f, 2.34f, -4.71f, 5.61f, -5.39f, 9.32f)
        curveToRelative(2.6f, 1.23f, 5.8f, 0.78f, 7.95f, -1.37f)
        curveTo(19.43f, 14.47f, 20f, 4f, 20f, 4f)
        reflectiveCurveTo(9.53f, 4.57f, 6.05f, 8.05f)
        close()
    }
}

// WbSunny — the "early bird / breakfast" achievement.
private val SunVector: ImageVector = materialIcon(name = "Filled.Sun") {
    materialPath {
        moveTo(12f, 7f)
        curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
        reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
        reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
        reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
        close()
        moveTo(11f, 2f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(11f, 19f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(2f, 11f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(2f)
        horizontalLineTo(2f)
        close()
        moveTo(19f, 11f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-3f)
        close()
    }
}

// NightsStay (moon) — the "night owl / dinner" achievement.
private val MoonVector: ImageVector = materialIcon(name = "Filled.Moon") {
    materialPath {
        moveTo(12f, 3f)
        curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
        reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
        reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
        curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
        curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
        curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
        curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
        curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
        close()
    }
}

// ChefHat (toque) — the "best cook" achievement. Distinct from CrownVector, which already marks the
// stats "best plate" podium; a chef's hat reads as the cook, not the dish.
private val ChefHatVector: ImageVector = materialIcon(name = "Filled.ChefHat") {
    materialPath {
        // Hat band.
        moveTo(7f, 17f)
        horizontalLineToRelative(10f)
        verticalLineToRelative(3.5f)
        horizontalLineToRelative(-10f)
        close()
        // Puffy crown (three lobes) resting on the band.
        moveTo(8f, 17f)
        curveTo(6f, 17f, 4.6f, 15.6f, 4.6f, 14f)
        curveTo(4.6f, 12.1f, 6.1f, 11f, 7.6f, 11f)
        curveTo(7.7f, 8.6f, 9.6f, 7f, 12f, 7f)
        curveTo(14.4f, 7f, 16.3f, 8.6f, 16.4f, 11f)
        curveTo(17.9f, 11f, 19.4f, 12.1f, 19.4f, 14f)
        curveTo(19.4f, 15.6f, 18f, 17f, 16f, 17f)
        close()
    }
}

// MoreVert — three-dot vertical overflow menu trigger (vendored from material-icons-extended,
// §build-conventions: iOS has no material-icons-extended publication in CMP 1.11.0).
private val MoreVertVector: ImageVector = materialIcon(name = "Filled.MoreVert") {
    materialPath {
        moveTo(12f, 8f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
        reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
        reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
        close()
        moveTo(12f, 10f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
        reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
        reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
        close()
        moveTo(12f, 16f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
        reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
        reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
        close()
    }
}

// Public (globe) — forward-hook cuisine/globe achievement.
private val PublicVector: ImageVector = materialIcon(name = "Filled.Public") {
    materialPath(pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
        reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
        reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
        reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
        close()
        moveTo(7f, 11f)
        horizontalLineToRelative(10f)
        verticalLineToRelative(2f)
        horizontalLineTo(7f)
        close()
        moveTo(11f, 7f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(10f)
        horizontalLineToRelative(-2f)
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
    val Star: ImageVector          = Icons.Filled.Star
    val Flame: ImageVector         = FlameVector
    val Place: ImageVector         = PlaceVector
    val Comment: ImageVector       = CommentVector
    val Group: ImageVector         = GroupVector
    val Warning: ImageVector       = WarningVector
    val Flag: ImageVector          = FlagVector
    val Block: ImageVector         = BlockVector
    val Delete: ImageVector        = DeleteVector
    // Profile / settings rows.
    val Person: ImageVector        = PersonVector
    val Theme: ImageVector         = DarkModeVector
    val Language: ImageVector      = LanguageVector
    val Notifications: ImageVector = NotificationsVector
    val Logout: ImageVector        = LogoutVector
    // ChevronLeft/Right are in material-icons-extended (no KMP iOS artifact); use
    // KeyboardArrowLeft/Right from material-icons-core automirrored as substitutes.
    val ChevronLeft: ImageVector   = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val ChevronRight: ImageVector  = Icons.AutoMirrored.Filled.KeyboardArrowRight
    // Achievement-badge glyphs.
    val Share: ImageVector         = Icons.Filled.Share
    val Restaurant: ImageVector    = RestaurantVector
    val Trophy: ImageVector        = TrophyVector
    val Eco: ImageVector           = EcoVector
    val Sun: ImageVector           = SunVector
    val Moon: ImageVector          = MoonVector
    val Public: ImageVector        = PublicVector
    val ChefHat: ImageVector       = ChefHatVector
    /** Three-dot vertical overflow icon (vendored from material-icons-extended, §build-conventions). */
    val MoreVert: ImageVector      = MoreVertVector
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
        "Place"         to FrIcons.Place,
        "Comment"       to FrIcons.Comment,
        "Group"         to FrIcons.Group,
        "Delete"        to FrIcons.Delete,
        "Warning"       to FrIcons.Warning,
        "Person"        to FrIcons.Person,
        "Theme"         to FrIcons.Theme,
        "Language"      to FrIcons.Language,
        "Notifications" to FrIcons.Notifications,
        "Logout"        to FrIcons.Logout,
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
