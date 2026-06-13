package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import kotlin.math.round

/**
 * Feed list "card row": square thumbnail with an outset [FrScoreBadge] overlay, the dish title,
 * an identity line (avatar + author + slot) and a meta line (time + score summary). When the
 * viewer has voted, a celebration-tinted "your vote" line is appended.
 *
 * The card lifts + scales on press — that behaviour comes from [FrCard]'s built-in press
 * feedback, so this row carries no bespoke animation. List padding/spacing is owned by the
 * caller (the feed's LazyColumn), so the row only fills width.
 */
@Composable
fun FrFeedMealRow(
    ui: FeedMealUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    val avg = ui.averageScore
    val hasVotes = avg != null && ui.ratingCount > 0
    val avgRounded = avg?.let { (round(it * 10) / 10.0).toString() }

    FrCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(Radius.lg),
        contentPadding = PaddingValues(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(Sizes.feedRowThumbnail)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (ui.photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ui.photoUrl,
                            contentDescription = ui.dishName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (avg != null && avgRounded != null && ui.ratingCount > 0) {
                    val rounded = round(avg).toInt().coerceIn(1, 10)
                    StarScoreBadge(
                        score = rounded,
                        contentDescription = resolve(FeedStringKey.RatingSummary, avgRounded, ui.ratingCount),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = Spacing.xs, y = Spacing.xs),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                FrText(
                    text = ui.dishName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // decorative — the adjacent author-name label carries the identity for screen readers.
                    FrAvatar(
                        initials = ui.authorName,
                        imageUrl = ui.authorAvatarUrl,
                        size = Sizes.avatarSm,
                    )
                    FrText(
                        text = ui.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Dot()
                    FrText(
                        text = resolve(ui.slot.labelKey()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FrText(
                        text = resolve(
                            FeedStringKey.TimeOfDay,
                            ui.publishedHour.toString().padStart(2, '0'),
                            ui.publishedMinute.toString().padStart(2, '0'),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Dot()
                    if (hasVotes && avgRounded != null) {
                        FrText(
                            text = resolve(FeedStringKey.RatingSummaryVotes, avgRounded, ui.ratingCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    } else {
                        FrText(
                            text = resolve(FeedStringKey.NoVotesYet),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                ui.viewerRating?.let { viewerRating ->
                    FrText(
                        text = resolve(FeedStringKey.YourVote, viewerRating),
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.celebration,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Star-shaped score badge overlaid on the feed thumbnail (replaces the old circular badge):
 * a celebration-tinted star with the rounded score centred on it, backed by a surface-coloured
 * star halo so it reads on any photo.
 */
@Composable
private fun StarScoreBadge(
    score: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    Box(
        modifier = modifier
            .size(Sizes.scoreStar)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FrIcon(
            image = FrIcons.Star,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(Sizes.scoreStar),
        )
        FrIcon(
            image = FrIcons.Star,
            tint = semantic.celebration,
            modifier = Modifier.size(Sizes.scoreStar - Spacing.xs),
        )
        FrText(
            text = score.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = semantic.onCelebration,
            // Nudge up to the star's optical centre — the lower points pull weight down.
            modifier = Modifier.offset(y = -Spacing.xxs),
        )
    }
}

/** Neutral round bullet used as an inline separator between meta segments. */
@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(Spacing.xs)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
