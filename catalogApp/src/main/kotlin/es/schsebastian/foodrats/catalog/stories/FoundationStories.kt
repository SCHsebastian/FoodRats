package es.schsebastian.foodrats.catalog.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.catalog.components.CatalogScene
import es.schsebastian.foodrats.catalog.components.CatalogSceneSplit
import es.schsebastian.foodrats.catalog.components.CatalogSectionHeader
import es.schsebastian.foodrats.catalog.components.CatalogSpecRow
import es.schsebastian.foodrats.catalog.components.CatalogSwatchGrid
import es.schsebastian.foodrats.catalog.registry.CatalogEntry
import es.schsebastian.foodrats.catalog.registry.CatalogGroup
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Elevation
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

internal fun foundationStories(): List<CatalogEntry> = listOf(
    CatalogEntry(
        id = "foundation.colors",
        group = CatalogGroup.FOUNDATIONS,
        title = "Colors",
        subtitle = "Material color scheme + FrSemanticColors",
        content = { ColorsStory() },
    ),
    CatalogEntry(
        id = "foundation.typography",
        group = CatalogGroup.FOUNDATIONS,
        title = "Typography",
        subtitle = "Material ramp + FrTextStyles (tabular numerals)",
        content = { TypographyStory() },
    ),
    CatalogEntry(
        id = "foundation.spacing",
        group = CatalogGroup.FOUNDATIONS,
        title = "Spacing",
        subtitle = "Spacing.xxs..xxl",
        content = { SpacingStory() },
    ),
    CatalogEntry(
        id = "foundation.radius",
        group = CatalogGroup.FOUNDATIONS,
        title = "Radius",
        subtitle = "Radius.xs..xl + pill",
        content = { RadiusStory() },
    ),
    CatalogEntry(
        id = "foundation.elevation",
        group = CatalogGroup.FOUNDATIONS,
        title = "Elevation",
        subtitle = "Elevation.level0..level5",
        content = { ElevationStory() },
    ),
    CatalogEntry(
        id = "foundation.shapes",
        group = CatalogGroup.FOUNDATIONS,
        title = "Shapes",
        subtitle = "MaterialTheme.shapes",
        content = { ShapesStory() },
    ),
    CatalogEntry(
        id = "foundation.sizes",
        group = CatalogGroup.FOUNDATIONS,
        title = "Sizes",
        subtitle = "Component size tokens (avatar, icon, shutter, …)",
        content = { SizesStory() },
    ),
    CatalogEntry(
        id = "foundation.motion",
        group = CatalogGroup.FOUNDATIONS,
        title = "Motion",
        subtitle = "Duration tokens + easing curves",
        content = { MotionStory() },
    ),
)

// region Colors
@Composable
private fun ColorsStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogSectionHeader("Brand roles — Material scheme")
        CatalogSceneSplit(label = "Primary / Secondary / Tertiary") {
            BrandRolesGrid()
        }
        CatalogSectionHeader("Surfaces & containers")
        CatalogSceneSplit(label = "Surface tokens") {
            SurfaceTokensGrid()
        }
        CatalogSectionHeader("Error")
        CatalogSceneSplit(label = "error / errorContainer") {
            CatalogSwatchGrid(
                items = with(MaterialTheme.colorScheme) {
                    listOf(
                        Triple("error", error, onError),
                        Triple("errorContainer", errorContainer, onErrorContainer),
                    )
                },
            )
        }
        CatalogSectionHeader("Semantic — FrSemanticColors")
        CatalogScene(
            label = "Light",
            description = "Meaning colors. Access via LocalFrSemanticColors.current. Don't alias Material brand roles for meaning.",
            lockedTheme = es.schsebastian.foodrats.catalog.theme.ThemeMode.Light,
        ) { SemanticRolesGrid() }
        CatalogScene(
            label = "Dark",
            lockedTheme = es.schsebastian.foodrats.catalog.theme.ThemeMode.Dark,
        ) { SemanticRolesGrid() }
    }
}

@Composable
private fun BrandRolesGrid() {
    val cs = MaterialTheme.colorScheme
    CatalogSwatchGrid(
        items = listOf(
            Triple("primary", cs.primary, cs.onPrimary),
            Triple("primaryContainer", cs.primaryContainer, cs.onPrimaryContainer),
            Triple("secondary", cs.secondary, cs.onSecondary),
            Triple("secondaryContainer", cs.secondaryContainer, cs.onSecondaryContainer),
            Triple("tertiary", cs.tertiary, cs.onTertiary),
            Triple("tertiaryContainer", cs.tertiaryContainer, cs.onTertiaryContainer),
        ),
    )
}

@Composable
private fun SurfaceTokensGrid() {
    val cs = MaterialTheme.colorScheme
    CatalogSwatchGrid(
        items = listOf(
            Triple("background", cs.background, cs.onBackground),
            Triple("surface", cs.surface, cs.onSurface),
            Triple("surfaceVariant", cs.surfaceVariant, cs.onSurfaceVariant),
            Triple("inverseSurface", cs.inverseSurface, cs.inverseOnSurface),
        ),
    )
}

@Composable
private fun SemanticRolesGrid() {
    val s = LocalFrSemanticColors.current
    CatalogSwatchGrid(
        items = listOf(
            Triple("success", s.success, s.onSuccess),
            Triple("warning", s.warning, s.onWarning),
            Triple("danger", s.danger, s.onDanger),
            Triple("info", s.info, s.onInfo),
            Triple("celebration", s.celebration, s.onCelebration),
            Triple("streakHot", s.streakHot, s.onStreakHot),
        ),
    )
}
// endregion

// region Typography
@Composable
private fun TypographyStory() {
    val items: List<Pair<String, androidx.compose.ui.text.TextStyle>> = with(MaterialTheme.typography) {
        listOf(
            "displayLarge"   to displayLarge,
            "displayMedium"  to displayMedium,
            "displaySmall"   to displaySmall,
            "headlineLarge"  to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall"  to headlineSmall,
            "titleLarge"     to titleLarge,
            "titleMedium"    to titleMedium,
            "titleSmall"     to titleSmall,
            "bodyLarge"      to bodyLarge,
            "bodyMedium"     to bodyMedium,
            "bodySmall"      to bodySmall,
            "labelLarge"     to labelLarge,
            "labelMedium"    to labelMedium,
            "labelSmall"     to labelSmall,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        CatalogSectionHeader("Material ramp")
        CatalogScene(label = "All styles") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items.forEach { (name, style) ->
                    Column {
                        Text(text = name, style = style)
                        Text(
                            text = "${style.fontSize} · ${style.lineHeight} · ${style.fontWeight}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        CatalogSectionHeader("FrTextStyles — tabular numerals")
        CatalogScene(
            label = "statNumber / statNumberSmall",
            description = "Tabular numerals keep widths stable when values change.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = "12345  ·  09876", style = FrTextStyles.statNumber)
                Text(text = "Streak  ·  42 days", style = FrTextStyles.statNumberSmall)
            }
        }
    }
}
// endregion

// region Spacing
@Composable
private fun SpacingStory() {
    val items: List<Pair<String, Dp>> = listOf(
        "xxs" to Spacing.xxs,
        "xs"  to Spacing.xs,
        "sm"  to Spacing.sm,
        "md"  to Spacing.md,
        "lg"  to Spacing.lg,
        "xl"  to Spacing.xl,
        "xxl" to Spacing.xxl,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Bars (relative size)") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items.forEach { (label, dp) ->
                    Column {
                        Text(
                            text = "Spacing.$label · $dp",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Box(
                            modifier = Modifier
                                .height(dp.coerceAtLeast(2.dp))
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.xs)),
                        )
                    }
                }
            }
        }
        CatalogScene(label = "Token table") {
            Column { items.forEach { (n, d) -> CatalogSpecRow("Spacing.$n", "$d") } }
        }
    }
}
// endregion

// region Radius
@Composable
private fun RadiusStory() {
    val items: List<Pair<String, Dp>> = listOf(
        "xs" to Radius.xs, "sm" to Radius.sm, "md" to Radius.md,
        "lg" to Radius.lg, "xl" to Radius.xl, "pill" to Radius.pill,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Rounded surfaces") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items.forEach { (label, dp) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = RoundedCornerShape(dp.coerceAtMost(60.dp / 2)),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {}
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        CatalogScene(label = "Token table") {
            Column { items.forEach { (n, d) -> CatalogSpecRow("Radius.$n", "$d") } }
        }
    }
}
// endregion

// region Elevation
@Composable
private fun ElevationStory() {
    val items: List<Pair<String, Dp>> = listOf(
        "level0" to Elevation.level0,
        "level1" to Elevation.level1,
        "level2" to Elevation.level2,
        "level3" to Elevation.level3,
        "level4" to Elevation.level4,
        "level5" to Elevation.level5,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Shadow ramp") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(vertical = Spacing.sm),
            ) {
                items.forEach { (label, dp) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = dp,
                            tonalElevation = dp,
                        ) {}
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        CatalogScene(label = "Token table") {
            Column { items.forEach { (n, d) -> CatalogSpecRow("Elevation.$n", "$d") } }
        }
    }
}
// endregion

// region Shapes
@Composable
private fun ShapesStory() {
    val shapes = MaterialTheme.shapes
    val items = listOf(
        "extraSmall" to shapes.extraSmall,
        "small"      to shapes.small,
        "medium"     to shapes.medium,
        "large"      to shapes.large,
        "extraLarge" to shapes.extraLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "MaterialTheme.shapes") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                items.forEach { (name, shape) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = shape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {}
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
// endregion

// region Motion
@Composable
private fun MotionStory() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogSectionHeader("Durations (ms)")
        CatalogScene(label = "Token table") {
            Column {
                CatalogSpecRow("Motion.quick",  "${Motion.quick} ms")
                CatalogSpecRow("Motion.short",  "${Motion.short} ms")
                CatalogSpecRow("Motion.medium", "${Motion.medium} ms")
                CatalogSpecRow("Motion.long",   "${Motion.long} ms")
            }
        }
        CatalogSectionHeader("Easing curves")
        CatalogScene(
            label = "Standard / Decelerated / Accelerated / Emphasized",
            description = "Use Decelerated on enter, Accelerated on exit, Emphasized for playful overshoot.",
        ) {
            Column {
                CatalogSpecRow("Motion.Standard",   "FastOutSlowIn (default)")
                CatalogSpecRow("Motion.Decelerated", "cubic(0.05, 0.7, 0.1, 1.0) — entry")
                CatalogSpecRow("Motion.Accelerated", "cubic(0.3, 0.0, 0.8, 0.15) — exit")
                CatalogSpecRow("Motion.Emphasized",  "cubic(0.2, 0.0, 0.0, 1.2) — overshoot")
            }
        }
    }
}
// endregion

// region Sizes
@Composable
private fun SizesStory() {
    val items: List<Pair<String, Dp>> = listOf(
        "avatarSm" to Sizes.avatarSm,
        "avatarMd" to Sizes.avatarMd,
        "avatarLg" to Sizes.avatarLg,
        "iconSm"   to Sizes.iconSm,
        "iconMd"   to Sizes.iconMd,
        "iconLg"   to Sizes.iconLg,
        "shutter"  to Sizes.shutter,
        "touchTarget" to Sizes.touchTarget,
        "streakBadge" to Sizes.streakBadge,
        "bottomBarHeight" to Sizes.bottomBarHeight,
        "mealCardImage"  to Sizes.mealCardImage,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CatalogScene(label = "Token table") {
            Column { items.forEach { (n, d) -> CatalogSpecRow("Sizes.$n", "$d") } }
        }
        CatalogScene(label = "Avatar / icon scale") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(Sizes.avatarSm, Sizes.avatarMd, Sizes.avatarLg).forEach { dp ->
                    Box(
                        modifier = Modifier
                            .size(dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }
        }
    }
}
// endregion

