package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Flame icon with a pulsing scale animation. [urgency] in 0f..1f drives the pulse
 * period: 0f → slow (~1.4s), 1f → fast (~0.5s). Use it from the streak hero card,
 * passing a value derived from how far into the day the user is.
 */
@Composable
fun FrFlameIcon(
    modifier: Modifier = Modifier,
    size: Dp = Sizes.iconLg,
    tint: Color = Color.Unspecified,
    urgency: Float = 0f,
    contentDescription: String? = null,
) {
    val clampedUrgency = urgency.coerceIn(0f, 1f)
    val periodMs = (1400f - 900f * clampedUrgency).toInt()
    val infinite = rememberInfiniteTransition(label = "frFlamePulse")
    val scale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "frFlameScale",
    )
    FrIcon(
        image = FrIcons.Flame,
        modifier = modifier.size(size).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        tint = tint,
        contentDescription = contentDescription,
    )
}
