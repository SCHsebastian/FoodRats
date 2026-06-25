package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * FoodRats brand mark — chef toque + crossed cutlery + ember rat face.
 *
 * Ported 1:1 from the design-system logo
 * (`docs/specs/2026-06-21-structural-redesign/assets/logo.svg`, viewBox `140×200`). The artwork is
 * scaled to fit [size] (square footprint, centered) and theme-resolved so it adapts to light/dark:
 * the SVG ink `#1A1C18` → [inkColor] (cutlery, eyes, nose, outlines), cream `#E8E6DE` → [toqueColor]
 * (toque fill), ember `#B0561E` → [faceColor] (rat head & outer ears), light-ember `#E6A47B` →
 * [innerEarColor] (inner ears). All slots stay overridable by callers.
 */
@Composable
fun FrLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    toqueColor: Color = MaterialTheme.colorScheme.surface,
    inkColor: Color = MaterialTheme.colorScheme.onSurface,
    faceColor: Color = MaterialTheme.colorScheme.secondary,
    innerEarColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Canvas(modifier = modifier.size(size)) {
        // Fit the 140×200 viewBox into the square draw area, preserving aspect & centering.
        val unit = minOf(this.size.width / 140f, this.size.height / 200f)
        val xOff = (this.size.width - 140f * unit) / 2f
        val yOff = (this.size.height - 200f * unit) / 2f
        fun pt(x: Float, y: Float) = Offset(xOff + x * unit, yOff + y * unit)
        fun len(v: Float) = v * unit

        val outline = Stroke(width = len(2f))
        // Cream-filled, ink-outlined ellipse — the toque puffs.
        fun toquePuff(cx: Float, cy: Float, rx: Float, ry: Float) {
            val tl = pt(cx - rx, cy - ry)
            val sz = Size(len(2f * rx), len(2f * ry))
            drawOval(toqueColor, tl, sz)
            drawOval(inkColor, tl, sz, style = outline)
        }
        // Flat-filled ellipse from an SVG center + radii — the rat face.
        fun oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) {
            drawOval(color, pt(cx - rx, cy - ry), Size(len(2f * rx), len(2f * ry)))
        }

        // Crossed cutlery, drawn behind the rat.
        drawLine(inkColor, pt(30f, 76f), pt(118f, 164f), strokeWidth = len(4f), cap = StrokeCap.Round)
        drawLine(inkColor, pt(30f, 76f), pt(40f, 86f), strokeWidth = len(9f), cap = StrokeCap.Round)
        val tip0 = pt(110f, 156f)
        val tip1 = pt(124f, 170f)
        val tip2 = pt(120f, 162f)
        val tip3 = pt(114f, 160f)
        drawPath(
            Path().apply {
                moveTo(tip0.x, tip0.y)
                lineTo(tip1.x, tip1.y)
                lineTo(tip2.x, tip2.y)
                lineTo(tip3.x, tip3.y)
                close()
            },
            inkColor,
        )
        drawLine(inkColor, pt(118f, 76f), pt(30f, 164f), strokeWidth = len(4f), cap = StrokeCap.Round)
        drawLine(inkColor, pt(122f, 80f), pt(114f, 72f), strokeWidth = len(4f), cap = StrokeCap.Round)
        drawLine(inkColor, pt(116f, 70f), pt(110f, 76f), strokeWidth = len(4f), cap = StrokeCap.Round)
        drawLine(inkColor, pt(108f, 66f), pt(104f, 72f), strokeWidth = len(4f), cap = StrokeCap.Round)
        drawCircle(inkColor, len(4f), pt(30f, 166f))
        drawCircle(inkColor, len(4f), pt(118f, 166f))

        // Chef toque.
        toquePuff(50.8f, 37.936f, 17.92f, 18.816f)
        toquePuff(89.2f, 37.936f, 17.92f, 18.816f)
        toquePuff(70f, 25.392f, 18.816f, 21.504f)
        val bandTl = pt(38f, 50.48f)
        val bandSz = Size(len(64f), len(11.52f))
        drawRoundRect(toqueColor, bandTl, bandSz, CornerRadius(len(2f)))
        drawRoundRect(inkColor, bandTl, bandSz, CornerRadius(len(2f)), style = outline)

        // Ember rat face.
        oval(faceColor, 70f, 116f, 38.64f, 40.48f)
        oval(faceColor, 46.08f, 84.72f, 11.96f, 13.8f)
        oval(faceColor, 93.92f, 84.72f, 11.96f, 13.8f)
        oval(innerEarColor, 46.08f, 86.56f, 6.44f, 8.28f)
        oval(innerEarColor, 93.92f, 86.56f, 6.44f, 8.28f)
        oval(inkColor, 57.12f, 112.32f, 3.68f, 4.6f)
        oval(inkColor, 82.88f, 112.32f, 3.68f, 4.6f)
        oval(inkColor, 70f, 132.56f, 4.6f, 3.68f)
    }
}
