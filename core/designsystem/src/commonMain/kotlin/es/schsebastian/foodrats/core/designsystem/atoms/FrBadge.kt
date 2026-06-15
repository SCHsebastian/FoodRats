package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.theme.TierBronze
import es.schsebastian.foodrats.core.designsystem.theme.TierGold
import es.schsebastian.foodrats.core.designsystem.theme.TierSilver
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/** Tier styling for a badge. Presentation-only enum — atoms never see domain types. */
enum class FrBadgeTier { None, Bronze, Silver, Gold }

private val BadgeCircle = 72.dp
private val RingStroke = 4.dp

/**
 * A single achievement badge as a pure design-system atom (spec §8.4): a tinted icon disc with a
 * title underneath and an optional caption slot ("3 / 10", "Earned May 4"). Takes **primitives and
 * a presentation enum only** — never a domain type. The achievement-aware wrapper
 * (`FrAchievementCard`) that maps an `AchievementStatus` → these props lives in the feature.
 *
 * - `earned` true → the disc fills with [tint] at full saturation, the icon renders on-tint.
 * - `earned` false → the disc is muted (surfaceVariant), the icon is dimmed, and a progress ring
 *   sweeps [progressFraction] (0f..1f) around the disc so "almost there" reads at a glance.
 *
 * [tier] adjusts the earned-state border so a bronze/silver/gold family is distinguishable without
 * relying on the (shared) icon glyph.
 */
@Composable
fun FrBadge(
    icon: ImageVector,
    title: String,
    earned: Boolean,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    tier: FrBadgeTier = FrBadgeTier.None,
    caption: String? = null,
    contentDescription: String? = null,
) {
    val fraction = progressFraction.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (earned) 1f else fraction,
        animationSpec = tween(durationMillis = 400),
        label = "FrBadgeProgress",
    )
    val ringColor = if (earned) tierColor(tier, tint) else tint
    val discColor = if (earned) tint else MaterialTheme.colorScheme.surfaceVariant
    val iconColor = if (earned) {
        contentColorFor(tint)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier.padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Progress / tier ring drawn behind the disc.
            Canvas(modifier = Modifier.size(BadgeCircle)) {
                val stroke = RingStroke.toPx()
                val inset = stroke / 2f
                // Track (always shown for locked badges; for earned it's the full tier ring).
                if (!earned) {
                    drawArc(
                        color = ringColor.copy(alpha = 0.20f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Surface(
                modifier = Modifier.size(BadgeCircle - RingStroke * 3),
                shape = CircleShape,
                color = discColor,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().alpha(if (earned) 1f else 0.55f),
                    contentAlignment = Alignment.Center,
                ) {
                    FrIcon(
                        image = icon,
                        contentDescription = contentDescription ?: title,
                        tint = iconColor,
                        modifier = Modifier.size(Sizes.iconLg),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun contentColorFor(tint: Color): Color {
    // Earned discs use the brand tint; white reads on the saturated Iron & Ember tints used by the
    // achievement icon mapper. Falls back to onPrimary so it stays theme-aware for the default tint.
    return if (tint == MaterialTheme.colorScheme.primary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        Color.White
    }
}

private fun tierColor(tier: FrBadgeTier, fallback: Color): Color = when (tier) {
    FrBadgeTier.Bronze -> TierBronze
    FrBadgeTier.Silver -> TierSilver
    FrBadgeTier.Gold -> TierGold
    FrBadgeTier.None -> fallback
}

@FrPreview
@Composable
private fun FrBadgePreview() {
    FrPreviewLightDark {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FrBadge(
                icon = FrIcons.Trophy,
                title = "First Plate",
                earned = true,
                progressFraction = 1f,
                caption = "Earned May 4",
            )
            FrBadge(
                icon = FrIcons.Restaurant,
                title = "Home Cook",
                earned = false,
                progressFraction = 0.6f,
                tier = FrBadgeTier.Silver,
                caption = "30 / 50",
            )
            FrBadge(
                icon = FrIcons.Moon,
                title = "Night Owl",
                earned = false,
                progressFraction = 0.1f,
                caption = "1 / 10",
            )
        }
    }
}
