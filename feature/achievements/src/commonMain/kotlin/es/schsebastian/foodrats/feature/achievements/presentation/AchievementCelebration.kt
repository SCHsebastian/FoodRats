package es.schsebastian.foodrats.feature.achievements.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val RAY_COUNT = 14
private val Tau = (kotlin.math.PI * 2).toFloat()

/**
 * A bespoke unlock celebration (spec §8.3) — not a stock dialog. Three independent timelines play at
 * once over a tap-to-dismiss scrim:
 *
 * 1. **`reveal`** — a critically-soft [spring] that scales the central medallion up past 1 and settles,
 *    and fades the scrim + copy in. This is the "stamp lands" beat.
 * 2. **`burst`** — a one-shot [tween] that flings [RAY_COUNT] rays outward and expands a ring, both
 *    fading to nothing as they travel. This is the firework.
 * 3. **`shimmer`** — a slow, infinite rotation that keeps the rays/ring drifting so a held celebration
 *    never looks frozen.
 *
 * All drawing is `Canvas` math (cross-platform); colors come from [LocalFrSemanticColors] (celebration
 * family), never a raw `Color(0x…)`.
 */
@Composable
internal fun AchievementUnlockedCelebration(
    titleKey: AchievementStringKey,
    onDismiss: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
            )
        }
        launch {
            burst.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = Motion.long, easing = Motion.Decelerated),
            )
        }
        launch {
            shimmer.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 9000, easing = LinearEasing)),
            )
        }
    }

    val semantic = LocalFrSemanticColors.current
    val celebration = semantic.celebration
    val onCelebration = semantic.onCelebration
    val scrimColor = MaterialTheme.colorScheme.scrim
    val dismissSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrimColor.copy(alpha = 0.62f * reveal.value.coerceIn(0f, 1f))) }
            .clickable(
                interactionSource = dismissSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(320.dp)) {
            val b = burst.value
            val r = reveal.value.coerceIn(0f, 1f)
            val maxR = size.minDimension / 2f
            val center = this.center

            // Expanding ring — grows from the medallion and dissolves.
            if (b > 0f) {
                drawCircle(
                    color = celebration.copy(alpha = (1f - b) * 0.7f * r),
                    radius = maxR * (0.32f + 0.55f * b),
                    center = center,
                    style = Stroke(width = 5.dp.toPx()),
                )
            }
            // Rays — flung outward, fading, slowly drifting via the infinite shimmer.
            rotate(degrees = shimmer.value * 360f, pivot = center) {
                val inner = maxR * (0.30f + 0.06f * b)
                val outer = inner + maxR * 0.46f * b
                repeat(RAY_COUNT) { i ->
                    val angle = Tau * i / RAY_COUNT
                    val ca = cos(angle)
                    val sa = sin(angle)
                    drawLine(
                        color = celebration.copy(alpha = (1f - b) * r),
                        start = Offset(center.x + ca * inner, center.y + sa * inner),
                        end = Offset(center.x + ca * outer, center.y + sa * outer),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.xl),
        ) {
            Surface(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = reveal.value
                        scaleY = reveal.value
                        alpha = reveal.value.coerceIn(0f, 1f)
                    },
                shape = CircleShape,
                color = celebration,
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FrIcon(
                        image = FrIcons.Trophy,
                        contentDescription = resolve(AchievementStringKey.CelebrationTitle),
                        tint = onCelebration,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            FrText(
                text = resolve(AchievementStringKey.CelebrationTitle),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) },
            )
            FrText(
                text = resolve(titleKey),
                style = MaterialTheme.typography.titleMedium,
                color = celebration,
                modifier = Modifier.graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) },
            )
            FrText(
                text = resolve(AchievementStringKey.CelebrationAck),
                style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f) }.padding(top = Spacing.sm),
            )
        }
    }
}
