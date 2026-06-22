package es.schsebastian.foodrats.feature.achievements.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.structural.FrBadgeDisc
import es.schsebastian.foodrats.core.designsystem.structural.FrBarTrack
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.achievements.domain.model.AchievementStatus
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * Structural badge cell: a glowing-or-frosted [FrBadgeDisc] over the name, the "earned on" date or
 * `current / target` progress caption, and (when locked) a thin [FrBarTrack]. Domain-aware (it touches
 * [AchievementStatus] + feature i18n), so it lives in the feature, never `:core:designsystem`.
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
    val title = resolve(status.achievement.titleKey)
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

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FrBadgeDisc(
            earned = earned,
            icon = status.achievement.iconKey.toVector(),
            size = 56.dp,
            contentDescription = title,
        )
        FrText(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = if (earned) StructuralColors.foreground else StructuralColors.foreground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        FrText(
            text = caption,
            style = StructuralType.microMono,
            color = StructuralColors.foreground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
        if (!earned) {
            FrBarTrack(progress = fraction, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs))
        }
    }
}
