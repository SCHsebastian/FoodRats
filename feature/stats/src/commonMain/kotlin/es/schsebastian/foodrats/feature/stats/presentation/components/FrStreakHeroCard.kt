package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrFlameIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.HeroStats
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

@Composable
fun FrStreakHeroCard(
    hero: HeroStats,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    val animated by animateIntAsState(
        targetValue = hero.personalStreak.days,
        animationSpec = tween(durationMillis = 800),
        label = "personalStreakCounter",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(Radius.lg))
            // The streak hero is the design system's single sanctioned gradient — forge-orange
            // through ember (secondary) to rust (tertiary). Everything else stays flat-semantic.
            .background(
                Brush.linearGradient(
                    listOf(
                        semantic.streakHot,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrFlameIcon(
                tint = semantic.onStreakHot,
                size = Sizes.iconLg,
                urgency = if (hero.personalStreak.days > 0) 0.6f else 0f,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (hero.personalStreak.days == 0) {
                    FrText(
                        text = resolve(StatsStringKey.HeroNoStreak),
                        color = semantic.onStreakHot,
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else {
                    val key = if (hero.personalStreak.days == 1)
                        StatsStringKey.HeroPersonalStreakSingular
                    else StatsStringKey.HeroPersonalStreakPlural
                    FrText(
                        text = resolve(key, animated),
                        color = semantic.onStreakHot,
                        style = FrTextStyles.statNumber,
                    )
                }
                val crewKey = if (hero.crewStreak.days == 1)
                    StatsStringKey.HeroCrewStreakSingular
                else StatsStringKey.HeroCrewStreakPlural
                FrText(
                    text = resolve(crewKey, hero.crewStreak.days),
                    color = semantic.onStreakHot,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
