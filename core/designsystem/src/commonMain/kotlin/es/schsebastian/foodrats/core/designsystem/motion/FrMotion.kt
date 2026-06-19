package es.schsebastian.foodrats.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * FoodRats motion language — hand-tuned spring physics shared across screens so each screen's
 * bespoke entrance feels like one coherent app. This is the connective tissue (the "house
 * physics"), NOT a replacement for per-screen signature motion: screens layer their own focal
 * choreography (hero reveals, count-ups, celebratory pops) on top of this baseline rhythm.
 *
 * Durations/easings for non-spring transitions still come from `tokens/Motion.kt`.
 */
object FrSpring {
    /** Lively overshoot — entrances, badges, celebratory pops. The signature FoodRats bounce. */
    val Bouncy: AnimationSpec<Float> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)

    /** Settled, no overshoot — content that should arrive calmly (rows, hero reveals, text). */
    val Gentle: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Bespoke "rise in" entrance: on first composition the node fades in while rising [riseDp] dp and
 * scaling up from [fromScale] with a soft overshoot, after [delayMillis]. Pass a per-index delay to
 * cascade a list/column (keep the window small so items scrolled into view later still pop promptly).
 *
 * Decorative — drives only `graphicsLayer`, costs nothing in layout, and runs once per node.
 */
@Composable
fun Modifier.frRiseIn(
    delayMillis: Int = 0,
    riseDp: Float = 28f,
    fromScale: Float = 0.92f,
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(targetValue = 1f, animationSpec = FrSpring.Bouncy)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * riseDp
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}

/**
 * Bespoke hero reveal: the node fades in while settling from a slight over-scale ([fromScale]) to 1
 * with a gentle, overshoot-free spring — a focal image/card "developing" into view. Use on a detail
 * screen's hero, a streak medallion, etc. Pair with a delayed [frRiseIn] on the surrounding text for
 * a layered reveal. Decorative; runs once.
 */
@Composable
fun Modifier.frRevealScale(fromScale: Float = 1.06f): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(targetValue = 1f, animationSpec = FrSpring.Gentle)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}
