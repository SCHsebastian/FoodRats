package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * Thin indeterminate progress bar pinned to the top of a screen. Used to
 * communicate that a long-running operation (e.g. background meal upload) is
 * in flight while the user keeps interacting with other surfaces (Feed, Stats).
 *
 * The bar slides + fades in/out via [AnimatedVisibility] so it doesn't pop into
 * the layout — when [visible] flips, the change is smooth.
 *
 * Use the design-system meaning role `success` for happy-path uploads and
 * `danger` for failures via [color] (default is Material primary).
 */
@Composable
fun FrUploadProgressBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = Motion.short,
                easing = Motion.Decelerated,
            ),
            initialOffsetY = { -it },
        ) + fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = Motion.short,
                easing = Motion.Decelerated,
            ),
        ),
        exit = slideOutVertically(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = Motion.short,
                easing = Motion.Accelerated,
            ),
            targetOffsetY = { -it },
        ) + fadeOut(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = Motion.short,
                easing = Motion.Accelerated,
            ),
        ),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(trackColor)) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = color,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}
