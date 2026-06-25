package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius

/** Fill/content tone of a [FrStructuralChip]: frosted neutral glass, or a loud streak-hot ember. */
enum class FrChipTone { Glass, Ember }

/**
 * A frosted pill chip / filter chip — the Structural variant's smallest interactive stratum.
 * Border-less and zero-chrome: it reads only as a translucent tint over the blurred media floor, never
 * as an outlined box. Selected swaps to the olive brand fill; [FrChipTone.Ember] is the rare streak-hot
 * loud state. `compact` shrinks it to the dense uppercase micro-pill.
 *
 * @param onClick when non-null the chip becomes a tappable filter with a press-scale cue and `Role.Button`.
 *   Its [selected] state is mirrored into semantics so TalkBack/VoiceOver announce "selected" instead of
 *   leaving the choice conveyed by fill color alone (WCAG 4.1.2 / 1.4.1).
 */
@Composable
fun FrStructuralChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: FrChipTone = FrChipTone.Glass,
    leadingIcon: ImageVector? = null,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val semantic = LocalFrSemanticColors.current
    val scheme = MaterialTheme.colorScheme

    val fill: Color
    val content: Color
    when {
        selected -> {
            fill = scheme.primary
            content = scheme.onPrimary
        }
        tone == FrChipTone.Ember -> {
            fill = semantic.streakHot
            content = semantic.onStreakHot
        }
        else -> {
            fill = StructuralColors.chip
            content = StructuralColors.foreground
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.97f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "chipPress",
    )

    val shape = RoundedCornerShape(Radius.pill)
    val height = if (compact) 24.dp else 30.dp
    val horizontalPadding = if (compact) 9.dp else 12.dp
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = if (compact) 10.sp else 12.sp,
        fontWeight = FontWeight.Bold,
    )

    val isSelected = selected
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .then(if (onClick != null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .height(height)
            .clip(shape)
            .background(fill, shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        // Announce the toggle state so selection isn't conveyed by color alone.
                        .semantics { this.selected = isSelected }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp),
                )
            }
            FrText(
                text = label,
                color = content,
                style = labelStyle,
            )
        }
    }
}

@FrPreview
@Composable
private fun FrStructuralChipPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FrStructuralChip("BREAKFAST")
                FrStructuralChip("LUNCH", selected = true)
                FrStructuralChip("HOT 9", tone = FrChipTone.Ember)
                FrStructuralChip("NEW", compact = true)
            }
        }
    }
}
