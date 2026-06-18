package es.schsebastian.foodrats.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.catalog.theme.LocalThemeMode
import es.schsebastian.foodrats.catalog.theme.ThemeMode
import es.schsebastian.foodrats.catalog.theme.isDark

/**
 * Renders a single scenario inside a labeled card. The body uses whichever theme the
 * user picked from the top bar (System/Light/Dark) via [LocalThemeMode]. Pass
 * `lockedTheme` for side-by-side light/dark comparison tiles (see [CatalogSceneSplit]).
 */
@Composable
fun CatalogScene(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    lockedTheme: ThemeMode? = null,
    padding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable () -> Unit,
) {
    val mode = lockedTheme ?: LocalThemeMode.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (lockedTheme != null) {
                Text(
                    text = "· ${lockedTheme.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        FoodRatsTheme(darkTheme = mode.isDark()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md)),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(modifier = Modifier.padding(padding)) { content() }
            }
        }
    }
}

/** Two cards side by side, locked to Light and Dark, so designers can compare without toggling. */
@Composable
fun CatalogSceneSplit(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    padding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(ThemeMode.Light, ThemeMode.Dark).forEach { mode ->
                FoodRatsTheme(darkTheme = mode.isDark()) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.md)),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(modifier = Modifier.padding(padding)) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Spacing.xs),
                            )
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Subsection header within a story page (e.g. "Variants", "Sizes", "States").
 * Renders in the catalog's outer theme — not affected by [LocalThemeMode].
 */
@Composable
fun CatalogSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

/** Spec-style row: monospace key and value, used for token tables (Spacing, Radius, …). */
@Composable
fun CatalogSpecRow(
    name: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Coloured swatch used in the Colors foundation page. */
@Composable
fun CatalogSwatch(
    name: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.sm),
        color = background,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
fun CatalogSwatchGrid(
    items: List<Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { (name, bg, fg) ->
                    CatalogSwatch(
                        name = name,
                        background = bg,
                        foreground = fg,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    Box(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }
}
