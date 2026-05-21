package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Day-navigator header. The label is wrapped in an [AnimatedContent] so it
 * slides + fades horizontally in the direction the user is navigating
 * (`previous` slides in from the left, `next` from the right). Chevrons
 * disable smoothly via `FrIconButton`'s built-in `enabled` styling.
 */
@Composable
fun FrFeedDayHeader(
    label: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FrIconButton(
            icon = FrIcons.ChevronLeft,
            onClick = onPrev,
            enabled = canGoPrev,
        )
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                // Compare lexicographically — feed day keys are ISO dates, so
                // larger key = more recent = "next" navigation.
                val forward = targetState > initialState
                val direction = if (forward) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(Motion.short, easing = Motion.Decelerated),
                ) { it * direction } + fadeIn(
                    animationSpec = tween(Motion.short, easing = Motion.Decelerated),
                )) togetherWith (slideOutHorizontally(
                    animationSpec = tween(Motion.short, easing = Motion.Accelerated),
                ) { -it * direction } + fadeOut(
                    animationSpec = tween(Motion.short, easing = Motion.Accelerated),
                ))
            },
            label = "feedDayLabel",
        ) { animatedLabel ->
            Box(modifier = Modifier.padding(horizontal = Spacing.sm)) {
                FrText(text = animatedLabel)
            }
        }
        FrIconButton(
            icon = FrIcons.ChevronRight,
            onClick = onNext,
            enabled = canGoNext,
        )
    }
}
