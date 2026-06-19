package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrCrownBadge
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage
import es.schsebastian.foodrats.feature.stats.domain.model.MemberCount
import es.schsebastian.foodrats.feature.stats.i18n.StatsPluralKey
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

enum class CookAwardVariant { BestCook, MostProlific }

/** Best cook variant — takes [MemberAverage]. */
@Composable
fun FrCookAwardCard(
    award: MemberAverage,
    variant: CookAwardVariant = CookAwardVariant.BestCook,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    val (background, onBackground) = when (variant) {
        CookAwardVariant.BestCook     -> semantic.celebration to semantic.onCelebration
        CookAwardVariant.MostProlific -> semantic.info to semantic.onInfo
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrCrownBadge(
            background = onBackground.copy(alpha = 0.2f),
            iconTint = onBackground,
        )
        FrAvatar(
            initials = award.displayName.take(2),
            imageUrl = award.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = resolve(titleFor(variant)),
                style = MaterialTheme.typography.labelMedium,
                color = onBackground.copy(alpha = 0.8f),
            )
            FrText(
                text = award.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = onBackground,
            )
            FrText(
                text = resolve(
                    StatsStringKey.BestCookMetricFormat,
                    formatOneDecimal(award.averageScore.toFloat()),
                    award.postCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = onBackground.copy(alpha = 0.9f),
            )
        }
    }
}

/** Most prolific variant — takes [MemberCount]. */
@Composable
fun FrCookAwardCard(
    award: MemberCount,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    val background = semantic.info
    val onBackground = semantic.onInfo
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrCrownBadge(
            background = onBackground.copy(alpha = 0.2f),
            iconTint = onBackground,
        )
        FrAvatar(
            initials = award.displayName.take(2),
            imageUrl = award.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = resolve(StatsStringKey.MostProlificTitle),
                style = MaterialTheme.typography.labelMedium,
                color = onBackground.copy(alpha = 0.8f),
            )
            FrText(
                text = award.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = onBackground,
            )
            FrText(
                text = resolvePlural(StatsPluralKey.MostProlificMetric, award.mealCount),
                style = MaterialTheme.typography.bodyMedium,
                color = onBackground.copy(alpha = 0.9f),
            )
        }
    }
}

private fun titleFor(variant: CookAwardVariant): StatsStringKey = when (variant) {
    CookAwardVariant.BestCook     -> StatsStringKey.BestCookTitle
    CookAwardVariant.MostProlific -> StatsStringKey.MostProlificTitle
}
