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
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.stats.domain.model.IngredientUsage
import es.schsebastian.foodrats.feature.stats.domain.model.MemberIngredient
import es.schsebastian.foodrats.feature.stats.i18n.StatsStringKey

/** Crew-wide most-used ingredient for a window: "name · N meals". */
@Composable
fun FrMostUsedIngredientCard(
    usage: IngredientUsage,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.secondaryContainer
    val onBackground = MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        FrText(
            text = resolve(StatsStringKey.MostUsedIngredientTitle),
            style = MaterialTheme.typography.labelMedium,
            color = onBackground.copy(alpha = 0.8f),
        )
        FrText(
            text = resolve(StatsStringKey.MostUsedIngredientMetricFormat, usage.displayName, usage.mealCount),
            style = MaterialTheme.typography.titleMedium,
            color = onBackground,
        )
    }
}

/** One crew member's signature ingredient: avatar + name + "ingredient · N". */
@Composable
fun FrMemberIngredientRow(
    member: MemberIngredient,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrAvatar(
            initials = member.displayName.take(2),
            imageUrl = member.avatarUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            FrText(
                text = member.displayName,
                style = MaterialTheme.typography.titleSmall,
            )
            FrText(
                text = resolve(StatsStringKey.MemberTopIngredientFormat, member.ingredientName, member.mealCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
