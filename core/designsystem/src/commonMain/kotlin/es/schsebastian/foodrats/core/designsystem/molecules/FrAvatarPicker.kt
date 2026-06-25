package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * Avatar preview (initials fallback) + change-avatar button. Tapping either is the edit
 * affordance — the avatar circle carries [changeLabel] as its click label so TalkBack
 * announces the action (WCAG 4.1.2 / 2.5.3) instead of focusing a silent click target.
 *
 * When [onRemoveClick] is non-null and an avatar is currently displayed ([avatarUrl] is
 * non-blank), a secondary "remove" button renders beside the change button so the user can
 * revert to initials. The remove button is hidden when no avatar is set (no-op UX).
 *
 * Takes only primitives so domain-aware callers (Profile, CrewSettings, …) can drive it.
 */
@Composable
fun FrAvatarPicker(
    initials: String,
    avatarUrl: String?,
    onPickClick: () -> Unit,
    busy: Boolean,
    changeLabel: String,
    uploadingLabel: String,
    modifier: Modifier = Modifier,
    onRemoveClick: (() -> Unit)? = null,
    removeLabel: String = "",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.avatarLg)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = !busy, onClickLabel = changeLabel) { onPickClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Sizes.avatarLg).clip(CircleShape),
                )
            } else {
                Text(
                    text = initials.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FrButton(
                label = if (busy) uploadingLabel else changeLabel,
                onClick = onPickClick,
                variant = FrButtonVariant.Secondary,
                enabled = !busy,
            )
            // Show remove only when an avatar is set and the callback is wired.
            if (onRemoveClick != null && !avatarUrl.isNullOrBlank()) {
                FrButton(
                    label = removeLabel,
                    onClick = onRemoveClick,
                    variant = FrButtonVariant.Ghost,
                    enabled = !busy,
                )
            }
        }
    }
}
