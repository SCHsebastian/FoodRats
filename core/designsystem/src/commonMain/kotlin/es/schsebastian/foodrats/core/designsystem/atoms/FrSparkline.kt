package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import kotlin.math.max

/**
 * Tiny inline trend chart for stat tiles / leaderboard rows. Draws a filled area under a stroked
 * line with a dot on the latest point. Pure Compose Canvas — no platform dependencies, renders
 * identically on Android and iOS.
 *
 * [data] is plotted left → right, auto-scaled to its own min/max. One or zero points draw a flat
 * mid-line. Size comes from [width]/[height] (a [modifier] size overrides them).
 */
@Composable
fun FrSparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 64.dp,
    height: Dp = 22.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier = Modifier.size(width = width, height = height).then(modifier)) {
        if (data.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val minV = data.minOrNull() ?: 0f
        val maxV = data.maxOrNull() ?: 0f
        val range = max(0.01f, maxV - minV)
        val points = if (data.size == 1) {
            listOf(Offset(0f, h / 2f), Offset(w, h / 2f))
        } else {
            val step = w / (data.size - 1)
            data.mapIndexed { i, v -> Offset(i * step, h - ((v - minV) / range) * h) }
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = area, color = color.copy(alpha = 0.15f))
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = color, radius = 2.dp.toPx(), center = points.last())
    }
}

@FrPreview
@Composable
private fun FrSparklinePreview() {
    FrPreviewLightDark {
        FrSparkline(
            data = listOf(7.4f, 8f, 8.6f, 7f, 9.2f, 8.5f, 9.2f),
            width = 120.dp,
            height = 36.dp,
        )
    }
}
