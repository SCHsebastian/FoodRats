package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/**
 * Hero container for the meal composer photo. Provides:
 * - Rounded clip with the medium radius token
 * - Subtle scale-in animation when content first appears
 * - 1:1 aspect ratio matching feed cards
 *
 * Caller supplies the photo composable (or skeleton) as `content`. The
 * animation is content-keyed: pass [contentKey] so the scale-in replays
 * when the photo changes (e.g. retaken via picker).
 */
@Composable
fun FrComposerHeroCard(
    contentKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (contentKey == null) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = Motion.medium,
            easing = Motion.Emphasized,
        ),
        label = "composerHeroScale",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(Radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}
