package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * App-wide "you're offline" notice pinned to the top of the screen.
 *
 * Painted with the design-system `warning` meaning role (amber) so it reads as a
 * recoverable degraded state, not an error — the device will reconnect and queued
 * work drains on its own. The text uses the matching `onWarning` foreground.
 *
 * Slides + fades in/out via [AnimatedVisibility] when [visible] flips, so the bar
 * doesn't pop into (or out of) the layout. The host passes `visible = !isOnline`.
 * [message] must already be resolved through `resolve(StringKey)` by the caller —
 * this atom takes a primitive string and never touches i18n or domain types.
 *
 * The bar is an assertive a11y live region so a screen reader announces the loss of
 * connectivity the moment the banner appears.
 */
@Composable
fun FrOfflineBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = Motion.short, easing = Motion.Decelerated),
            initialOffsetY = { -it },
        ) + fadeIn(
            animationSpec = tween(durationMillis = Motion.short, easing = Motion.Decelerated),
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = Motion.short, easing = Motion.Accelerated),
            targetOffsetY = { -it },
        ) + fadeOut(
            animationSpec = tween(durationMillis = Motion.short, easing = Motion.Accelerated),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(semantic.warning)
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrText(
                text = message,
                color = semantic.onWarning,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
