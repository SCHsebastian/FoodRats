package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultPlateColor = Color(0xFFE8E6DE)
private val DefaultEarColor = Color(0xFFB0561E)

@Composable
fun FrLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    plateColor: Color = DefaultPlateColor,
    earColor: Color = DefaultEarColor,
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
