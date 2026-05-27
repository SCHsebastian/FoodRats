package es.schsebastian.foodrats.feature.meal.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Single row that toggles between three visual states:
 *  - **idle**     → "Pin where you ate" pill, primary-container background
 *  - **locating** → spinning pin + "Finding you…" label
 *  - **pinned**   → coordinate chip + tap to clear, accented background
 *
 * The chip uses `AnimatedContent` so transitions between states fade through.
 */
@Composable
fun LocationPickerRow(
    idleLabel: String,
    locatingLabel: String,
    clearLabel: String,
    coordinatesLabel: String?,
    locating: Boolean,
    onRequest: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = when {
        locating              -> Mode.Locating
        coordinatesLabel != null -> Mode.Pinned
        else                  -> Mode.Idle
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "locationPickerRow",
        modifier = modifier.fillMaxWidth(),
    ) { mode ->
        when (mode) {
            Mode.Idle -> Pill(
                label = idleLabel,
                background = MaterialTheme.colorScheme.primaryContainer,
                onClick = onRequest,
            )
            Mode.Locating -> Pill(
                label = locatingLabel,
                background = MaterialTheme.colorScheme.surfaceVariant,
                onClick = {},
                spinning = true,
                iconAlpha = 0.7f,
            )
            Mode.Pinned -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    label = coordinatesLabel ?: "",
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    label = clearLabel,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onClear,
                    iconOverride = FrIcons.Close,
                )
            }
        }
    }
}

private enum class Mode { Idle, Locating, Pinned }

@Composable
private fun Pill(
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    iconAlpha: Float = 1f,
    iconOverride: ImageVector? = null,
) {
    val rotation = if (spinning) {
        val transition = rememberInfiniteTransition(label = "pillSpin")
        val spin by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1200)),
            label = "spin",
        )
        spin
    } else 0f
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FrIcon(
            image = iconOverride ?: FrIcons.Place,
            contentDescription = null,
            modifier = Modifier.size(18.dp).alpha(iconAlpha).rotate(rotation),
        )
        FrText(text = label)
    }
}
