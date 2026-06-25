package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.FrTextStyles
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * The Structural list row — **divider-less**. Stacked rows are separated only by a 1px inner
 * top-*light* (white @ 5%, [StructuralColors.hairline]) on every row but the first, never a solid
 * rule. Zero-chrome: no background, no outline; the row reads as floating content over the media
 * floor. Optional [leading]/[trailing] slots flank a weighted content column; tapping (when
 * [onClick] is set) gives the standard press-scale physics cue.
 */
@Composable
fun FrStructuralRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showTopHairline: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 0.98f else 1f,
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "rowPress",
    )
    val hairline = StructuralColors.hairline // DrawScope lambdas are not @Composable.

    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.graphicsLayer { scaleX = scale; scaleY = scale } else Modifier)
            .then(
                if (showTopHairline) {
                    Modifier.drawBehind {
                        drawLine(
                            color = hairline,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), content = content)
        trailing?.invoke()
    }
}

@FrPreview
@Composable
private fun FrStructuralRowPreview() {
    FoodRatsTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .background(StructuralColors.stageFloor)
                .padding(24.dp),
        ) {
            Column {
                listOf(false, true, true).forEach { topHairline ->
                    FrStructuralRow(
                        showTopHairline = topHairline,
                        onClick = {},
                        leading = {
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            )
                        },
                        trailing = {
                            FrText(
                                "9",
                                color = StructuralColors.foreground,
                                style = FrTextStyles.statNumberSmall,
                            )
                        },
                    ) {
                        FrText(
                            "Saturday Brunch",
                            color = StructuralColors.foreground,
                            style = StructuralType.titleMd,
                        )
                        FrText(
                            "LAST SEEN 2H AGO",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = StructuralType.micro,
                        )
                    }
                }
            }
        }
    }
}
