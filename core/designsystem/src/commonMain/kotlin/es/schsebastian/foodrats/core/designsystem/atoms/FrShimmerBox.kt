package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * Skeleton placeholder with a horizontal shimmer sweep. Used while reactive
 * state (snapshot, historic, etc.) is still null. Pass a [shape] to match the
 * silhouette of the eventual content (rounded rectangle for cards, circle for
 * avatars).
 */
@Composable
fun FrShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "frShimmer")
    val phase by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "frShimmerPhase",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(phase - 300f, 0f),
        end = Offset(phase, 0f),
    )
    Box(modifier = modifier.clip(shape).background(brush))
}
