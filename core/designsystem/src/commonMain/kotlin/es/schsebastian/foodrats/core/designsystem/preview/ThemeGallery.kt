package es.schsebastian.foodrats.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

// One file gathering theme + token previews so the gallery is browseable from a single
// location. Each preview is rendered light + dark via [FrPreviewLightDark].

@FrPreview
@Composable
private fun ColorSchemePreview() {
    FrPreviewLightDark {
        val s = MaterialTheme.colorScheme
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorRow("primary",              s.primary,              s.onPrimary)
            ColorRow("primaryContainer",     s.primaryContainer,     s.onPrimaryContainer)
            ColorRow("secondary",            s.secondary,            s.onSecondary)
            ColorRow("secondaryContainer",   s.secondaryContainer,   s.onSecondaryContainer)
            ColorRow("tertiary",             s.tertiary,             s.onTertiary)
            ColorRow("tertiaryContainer",    s.tertiaryContainer,    s.onTertiaryContainer)
            ColorRow("error",                s.error,                s.onError)
            ColorRow("errorContainer",       s.errorContainer,       s.onErrorContainer)
            ColorRow("background",           s.background,           s.onBackground)
            ColorRow("surface",              s.surface,              s.onSurface)
            ColorRow("surfaceVariant",       s.surfaceVariant,       s.onSurfaceVariant)
            ColorRow("outline",              s.outline,              s.onSurface)
            ColorRow("outlineVariant",       s.outlineVariant,       s.onSurface)
        }
    }
}

@FrPreview
@Composable
private fun SemanticColorsPreview() {
    FrPreviewLightDark {
        val s = LocalFrSemanticColors.current
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorRow("success",     s.success,     s.onSuccess)
            ColorRow("warning",     s.warning,     s.onWarning)
            ColorRow("danger",      s.danger,      s.onDanger)
            ColorRow("info",        s.info,        s.onInfo)
            ColorRow("celebration", s.celebration, s.onCelebration)
            ColorRow("streakHot",   s.streakHot,   s.onStreakHot)
        }
    }
}

@FrPreview
@Composable
private fun TypographyPreview() {
    FrPreviewLightDark {
        val t = MaterialTheme.typography
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TypeRow("displayLarge",   t.displayLarge)
            TypeRow("displayMedium",  t.displayMedium)
            TypeRow("displaySmall",   t.displaySmall)
            TypeRow("headlineLarge",  t.headlineLarge)
            TypeRow("headlineMedium", t.headlineMedium)
            TypeRow("headlineSmall",  t.headlineSmall)
            TypeRow("titleLarge",     t.titleLarge)
            TypeRow("titleMedium",    t.titleMedium)
            TypeRow("titleSmall",     t.titleSmall)
            TypeRow("bodyLarge",      t.bodyLarge)
            TypeRow("bodyMedium",     t.bodyMedium)
            TypeRow("bodySmall",      t.bodySmall)
            TypeRow("labelLarge",     t.labelLarge)
            TypeRow("labelMedium",    t.labelMedium)
            TypeRow("labelSmall",     t.labelSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            TypeRow("statNumber",      FrTextStyles.statNumber, sample = "1,234")
            TypeRow("statNumberSmall", FrTextStyles.statNumberSmall, sample = "42")
        }
    }
}

@FrPreview
@Composable
private fun ShapesPreview() {
    FrPreviewLightDark {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            listOf(
                "xs"   to Radius.xs,
                "sm"   to Radius.sm,
                "md"   to Radius.md,
                "lg"   to Radius.lg,
                "xl"   to Radius.xl,
                "pill" to Radius.pill,
            ).forEach { (label, r) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(r))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@FrPreview
@Composable
private fun ElevationPreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "0" to Elevation.level0,
                "1" to Elevation.level1,
                "2" to Elevation.level2,
                "3" to Elevation.level3,
                "4" to Elevation.level4,
                "5" to Elevation.level5,
            ).forEach { (label, dp) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        tonalElevation = dp,
                        shadowElevation = dp,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                    ) {}
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@FrPreview
@Composable
private fun SpacingPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "xxs (2)" to Spacing.xxs,
                "xs (4)"  to Spacing.xs,
                "sm (8)"  to Spacing.sm,
                "md (16)" to Spacing.md,
                "lg (24)" to Spacing.lg,
                "xl (32)" to Spacing.xl,
                "xxl (64)" to Spacing.xxl,
            ).forEach { (label, dp) -> SpacingRow(label, dp) }
        }
    }
}

@FrPreview
@Composable
private fun SizesPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "avatarSm"     to Sizes.avatarSm,
                "avatarMd"     to Sizes.avatarMd,
                "avatarLg"     to Sizes.avatarLg,
                "iconSm"       to Sizes.iconSm,
                "iconMd"       to Sizes.iconMd,
                "iconLg"       to Sizes.iconLg,
                "shutter"      to Sizes.shutter,
                "streakBadge"  to Sizes.streakBadge,
                "mealCardImg"  to Sizes.mealCardImage,
                "bottomBarH"   to Sizes.bottomBarHeight,
                "touchTarget"  to Sizes.touchTarget,
            ).forEach { (label, dp) -> SpacingRow(label, dp) }
        }
    }
}

@Composable
private fun ColorRow(name: String, color: Color, on: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = on, style = MaterialTheme.typography.labelMedium)
        Text("Aa", color = on, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TypeRow(name: String, style: TextStyle, sample: String = "FoodRats") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(sample, style = style)
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpacingRow(label: String, value: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(100.dp),
        )
        Box(
            modifier = Modifier
                .height(8.dp)
                .width(value)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
