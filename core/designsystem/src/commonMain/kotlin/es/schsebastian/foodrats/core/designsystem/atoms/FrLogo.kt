package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FrLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    // Theme-resolved so the mark adapts to light/dark — plate = surface (concrete /
    // charcoal), ears = ember-copper secondary. Both stay overridable by callers.
    plateColor: Color = MaterialTheme.colorScheme.surface,
    earColor: Color = MaterialTheme.colorScheme.secondary,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val unit = w / 108f
        val plate = Offset(54f * unit, 62f * unit)
        val plateR = 24f * unit
        val centerEar = Offset(54f * unit, 32f * unit)
        val centerEarR = 9f * unit
        val leftEar = Offset(37f * unit, 38f * unit)
        val rightEar = Offset(71f * unit, 38f * unit)
        val sideEarR = 7f * unit

        drawCircle(earColor, sideEarR, leftEar)
        drawCircle(earColor, centerEarR, centerEar)
        drawCircle(earColor, sideEarR, rightEar)
        drawCircle(plateColor, plateR, plate)
        // height read to silence unused-variable warning if a future variant needs it
        @Suppress("UNUSED_EXPRESSION") h
    }
}
