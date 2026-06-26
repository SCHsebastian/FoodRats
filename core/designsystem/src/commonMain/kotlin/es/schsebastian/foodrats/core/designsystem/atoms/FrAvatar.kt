package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes

/**
 * Circular avatar that layers an uploaded image (via Coil [AsyncImage]) over an always-present
 * initials background. The initials show through while the image is null/blank, loading, or failed
 * (the [AsyncImage] is transparent until the bitmap resolves), so no subcomposition or extra measure
 * pass is needed.
 *
 * Decorative by default — `contentDescription` is null and the badge is `clearAndSetSemantics { }`-ed
 * out of the a11y tree. When it appears next to a name label, the name label carries the meaning
 * and the avatar would just produce a duplicate announcement. Pass an explicit
 * `contentDescription` for the rare case where the avatar stands alone.
 */
@Composable
fun FrAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatarMd,
    imageUrl: String? = null,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(semantics),
        contentAlignment = Alignment.Center,
    ) {
        InitialsContent(initials, Modifier.fillMaxSize())
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun InitialsContent(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
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
