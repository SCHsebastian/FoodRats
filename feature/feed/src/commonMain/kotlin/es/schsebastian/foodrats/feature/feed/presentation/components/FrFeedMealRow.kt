package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

/**
 * Compact list row: ~64dp thumbnail on the left, dish + author lines in the middle,
 * average-score badge on the right. Coil downsamples the photo to the layout size so
 * the row loads fast even on a long feed; the full-quality image lives on the detail
 * screen the row navigates to.
 */
@Composable
fun FrFeedMealRow(
    ui: FeedMealUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val thumbnailShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(thumbnailShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (ui.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = ui.photoUrl,
                    contentDescription = ui.dishName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp),
                )
            } else {
                FrAvatar(
                    initials = ui.authorName,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FrText(
                text = ui.dishName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FrText(
                text = ui.authorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val avg = ui.averageScore
        if (avg != null && ui.ratingCount > 0) {
            val rounded = avg.toInt().coerceIn(1, 10)
            FrScoreBadge(
                score = rounded,
                contentDescription = resolve(
                    FeedStringKey.RatingSummary,
                    (kotlin.math.round(avg * 10) / 10.0).toString(),
                    ui.ratingCount,
                ),
            )
        } else {
            FrText(
                text = resolve(FeedStringKey.NoVotesYet),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
