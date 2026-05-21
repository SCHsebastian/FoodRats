package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Circular crown badge for podium / "best plate" callouts. Decorative by default
 * — the surrounding card/row carries the meaning, so `contentDescription` is null
 * unless explicitly provided.
 */
@Composable
fun FrCrownBadge(
    modifier: Modifier = Modifier,
    iconSize: Dp = Sizes.iconMd,
    background: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(iconSize + 16.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        FrIcon(
            image = FrIcons.Crown,
            modifier = Modifier.padding(4.dp).size(iconSize),
            tint = iconTint,
            contentDescription = contentDescription,
        )
    }
}
