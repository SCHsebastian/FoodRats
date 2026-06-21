package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Passport "badge" disc — a zero-chrome circular achievement glyph. Earned discs glow with the
 * ember→streak gradient and a soft drop shadow; locked discs recede to a near-transparent frosted
 * fill, reading as a held slot rather than a bordered box. Extreme contrast, no outline.
 */
@Composable
fun FrBadgeDisc(
    earned: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalFrSemanticColors.current

    val contentColor: Color =
        if (earned) StructuralColors.foreground else scheme.onSurfaceVariant
    val background: Modifier =
        if (earned) {
            Modifier.background(
                brush = Brush.linearGradient(listOf(scheme.secondary, semantic.streakHot)),
                shape = CircleShape,
            )
        } else {
            Modifier.background(
                color = StructuralColors.foreground.copy(alpha = 0.10f),
                shape = CircleShape,
            )
        }

    Box(
        modifier = modifier
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .size(size)
            .then(
                if (earned) {
                    Modifier.shadow(elevation = 10.dp, shape = CircleShape, clip = false)
                } else {
                    Modifier
                },
            )
            .then(background),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * The thin progress-bar track for the passport — a pill-clipped frosted rail with a flush brand-olive
 * fill. Zero chrome: no border, no chrome height, just two stacked pills that read as a hard data bar
 * over the media floor.
 */
@Composable
fun FrBarTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    trackColor: Color = StructuralColors.foreground.copy(alpha = 0.16f),
    fillColor: Color = MaterialTheme.colorScheme.primary,
) {
    val pill = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(pill)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(pill)
                .background(fillColor),
        )
    }
}

@FrPreview
@Composable
private fun FrPassportBitsPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    FrBadgeDisc(earned = true, icon = Icons.Filled.Star)
                    FrBadgeDisc(earned = false, icon = Icons.Filled.Lock)
                }
                FrBarTrack(
                    progress = 0.6f,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                )
            }
        }
    }
}
