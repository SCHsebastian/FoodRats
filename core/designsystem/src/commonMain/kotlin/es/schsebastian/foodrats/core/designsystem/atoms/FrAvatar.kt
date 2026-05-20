package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Initial-circle avatar. Decorative by default — `contentDescription` is null and the badge
 * is `clearAndSetSemantics { }`-ed out of the a11y tree. When it appears next to a name
 * label, the name label carries the meaning and the avatar would just produce a duplicate
 * announcement. Pass an explicit `contentDescription` for the rare case where the avatar
 * stands alone.
 */
@Composable
fun FrAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatarMd,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@FrPreview
@Composable
private fun FrAvatarPreview() {
    FrPreviewLightDark {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrAvatar(initials = "sc", size = Sizes.avatarSm)
            FrAvatar(initials = "an", size = Sizes.avatarMd)
            FrAvatar(initials = "rk", size = Sizes.avatarLg)
            FrAvatar(initials = "tt", size = Sizes.avatarMd, contentDescription = "Trent T. avatar")
        }
    }
}
