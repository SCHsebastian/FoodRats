package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

enum class FrSettingsRowTone { Neutral, Danger }

/**
 * One row inside a [FrSettingsSection]. Layout:
 *
 *   [icon]  [title]                         [trailing]
 *           [subtitle (optional, dim)]
 *
 * Pass [onClick] for drill-downs (chevron auto-appended unless [trailing] is
 * supplied). Pass [trailing] to host a switch / value / custom chip.
 */
@Composable
fun FrSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    tone: FrSettingsRowTone = FrSettingsRowTone.Neutral,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor = when (tone) {
        FrSettingsRowTone.Neutral -> MaterialTheme.colorScheme.onSurface
        FrSettingsRowTone.Danger  -> MaterialTheme.colorScheme.error
    }.let { if (enabled) it else it.copy(alpha = 0.5f) }

    val iconTint = when (tone) {
        FrSettingsRowTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        FrSettingsRowTone.Danger  -> MaterialTheme.colorScheme.error
    }.let { if (enabled) it else it.copy(alpha = 0.5f) }

    val rowModifier = modifier
        .fillMaxWidth()
        .then(
            if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
        )
        .padding(horizontal = Spacing.md, vertical = Spacing.md)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(Sizes.iconMd),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            FrText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                FrText(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(contentAlignment = Alignment.CenterEnd) {
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(
                    imageVector = FrIcons.ChevronRight,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(Sizes.iconSm),
                )
                else -> Spacer(Modifier.width(0.dp))
            }
        }
    }
}
