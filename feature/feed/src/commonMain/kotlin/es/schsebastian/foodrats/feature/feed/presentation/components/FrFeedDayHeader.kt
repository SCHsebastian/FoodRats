package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

/** What [FrFeedDayHeader] animates between. [sortKey] (an ISO date) drives the slide direction. */
private data class DayLabel(val primary: String, val secondary: String, val sortKey: String)

/**
 * Day-navigator rendered as a rounded "pill" [Surface] (elevation-1) with the chevrons inset at
 * each end and the day label stacked + centre-aligned: a primary line (e.g. "Today") over an
 * optional secondary date line.
 *
 * The label is wrapped in an [AnimatedContent] so it slides + fades horizontally in the direction
 * the user is navigating — `previous` slides in from the left, `next` from the right. Direction is
 * decided by comparing [sortKey]s (ISO dates), so the relative-word primary labels don't confuse it.
 */
@Composable
fun FrFeedDayHeader(
    primaryLabel: String,
    secondaryLabel: String,
    sortKey: String,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Elevation.level1,
        shadowElevation = Elevation.level1,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FrIconButton(
                icon = FrIcons.ChevronLeft,
                onClick = onPrev,
                contentDescription = resolve(FeedStringKey.PrevDay),
                enabled = canGoPrev,
            )
            AnimatedContent(
                targetState = DayLabel(primaryLabel, secondaryLabel, sortKey),
                transitionSpec = {
                    // Larger ISO sort key = more recent = "next" navigation.
                    val forward = targetState.sortKey > initialState.sortKey
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
            ) { animated ->
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FrText(
                        text = animated.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (animated.secondary.isNotBlank()) {
                        FrText(
                            text = animated.secondary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FrIconButton(
                icon = FrIcons.ChevronRight,
                onClick = onNext,
                contentDescription = resolve(FeedStringKey.NextDay),
                enabled = canGoNext,
            )
        }
    }
}
