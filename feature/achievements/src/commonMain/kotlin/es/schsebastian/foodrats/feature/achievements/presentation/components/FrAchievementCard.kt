package es.schsebastian.foodrats.feature.achievements.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import es.schsebastian.foodrats.core.designsystem.atoms.FrBadge
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Domain-aware wrapper that maps an [AchievementStatus] → [FrBadge]'s primitive props (spec §8.3,
 * §8.4). Lives in the **feature** because it touches domain types and resolves feature i18n — the
 * same rule that keeps `FrMealCard` out of `:core:designsystem`. The pure atom stays domain-free.
 *
 * Earned = `unlockedAtEpochMs != null`; the caption shows the formatted "earned on" date for an
 * earned badge, or `current / target` progress for a locked one.
 */
@Composable
internal fun FrAchievementCard(
    status: AchievementStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unlockedAt = status.unlockedAtEpochMs
    val earned = unlockedAt != null
    val target = status.progress.target.coerceAtLeast(1)
    val fraction = status.progress.current.toFloat() / target.toFloat()
    val caption = if (unlockedAt != null) {
        resolve(AchievementStringKey.EarnedOnFormat, formatEpochDay(unlockedAt))
    } else {
        resolve(AchievementStringKey.ProgressFormat, status.progress.current, status.progress.target)
    }
    // Bespoke press feedback: the badge dips toward the finger and springs back on release.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = Motion.quick, easing = Motion.Standard),
        label = "FrAchievementCardPress",
    )
    FrBadge(
        icon = status.achievement.iconKey.toVector(),
        title = resolve(status.achievement.titleKey),
        earned = earned,
        progressFraction = fraction,
        tint = status.achievement.iconKey.tint(),
        tier = status.achievement.tier.toBadgeTier(),
        caption = caption,
        contentDescription = resolve(status.achievement.titleKey),
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
    )
}
