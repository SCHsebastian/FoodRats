package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

enum class FrSettingsSectionTone { Neutral, Danger }

/**
 * Grouped settings card: a header row above a rounded surface that hosts
 * stacked [FrSettingsRow]s separated by hairline dividers. The [Danger] tone
 * tints the border and header copy with the semantic danger color — use only
 * for sections containing irreversible actions.
 */
@Composable
fun FrSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: FrSettingsSectionTone = FrSettingsSectionTone.Neutral,
    content: @Composable () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    val headerColor = when (tone) {
        FrSettingsSectionTone.Neutral -> MaterialTheme.colorScheme.onSurface
        FrSettingsSectionTone.Danger  -> semantic.danger
    }
    Column(modifier = modifier.fillMaxWidth()) {
        FrText(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = headerColor,
            modifier = Modifier
                .padding(start = Spacing.md, bottom = Spacing.xs)
                .semantics { heading() },   // WCAG 2.4.10 — TalkBack heading navigation
        )
        subtitle?.let {
            FrText(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
            )
        }
        val shape = RoundedCornerShape(Radius.md)
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tone == FrSettingsSectionTone.Danger) {
                        Modifier.border(1.dp, semantic.danger.copy(alpha = 0.35f), shape)
                    } else Modifier,
                ),
            content = {
                Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                    content()
                }
            },
        )
        Spacer(Modifier.height(Spacing.xs))
    }
}

/**
 * Hairline divider between [FrSettingsRow]s within a [FrSettingsSection].
 */
@Composable
fun FrSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.md),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}
