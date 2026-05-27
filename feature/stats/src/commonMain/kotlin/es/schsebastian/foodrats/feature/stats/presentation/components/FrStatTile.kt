package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrSparkline
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * One summary tile in the stats grid: a tinted icon chip with an optional [spark] trend line,
 * a tabular stat [value], and an uppercase [label]. All text is supplied pre-resolved by the
 * caller — this is presentation-only and holds no domain types.
 */
@Composable
fun FrStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    spark: List<Float>? = null,
) {
    FrCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(Sizes.iconLg)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(tint.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FrIcon(image = icon, tint = tint, modifier = Modifier.size(Sizes.iconSm))
                }
                if (spark != null && spark.size >= 2) {
                    FrSparkline(data = spark, color = tint, width = 62.dp, height = 20.dp)
                }
            }
            FrText(
                text = value,
                style = FrTextStyles.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FrText(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
