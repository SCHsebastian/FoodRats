package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

@Composable
fun FrCommentRow(
    displayName: String,
    avatarUrl: String?,
    text: String,
    relative: RelativeTimestamp,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    isDeleted: Boolean = false,
) {
    val nameLabel = when {
        isDeleted -> resolve(FeedStringKey.DeletedAuthor)
        loading   -> "…"
        else      -> displayName
    }
    val avatarInitials = when {
        isDeleted -> "?"
        loading   -> "·"
        else      -> displayName.ifBlank { "?" }
    }
    val avatarImage = if (isDeleted || loading) null else avatarUrl

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        FrAvatar(initials = avatarInitials, imageUrl = avatarImage)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FrText(text = nameLabel, style = MaterialTheme.typography.labelLarge)
                FrText(
                    text = resolve(relative.key, relative.amount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            FrText(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
