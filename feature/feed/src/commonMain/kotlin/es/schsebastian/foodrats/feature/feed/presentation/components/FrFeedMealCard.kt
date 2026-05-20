package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarWithName
import es.schsebastian.foodrats.core.designsystem.molecules.FrStarRatingPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrTagChipRow
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey

@Composable
fun FrFeedMealCard(
    ui: FeedMealUi,
    onRate: (mealId: String, score: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            FrText(text = ui.dayEmote, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FrAvatarWithName(
                    initials = ui.authorName.take(2).uppercase(),
                    name = ui.authorName,
                )
                if (ui.averageScore != null) {
                    val avg = (kotlin.math.round(ui.averageScore * 10) / 10.0).toString()
                    FrText(text = resolve(FeedStringKey.RatingSummary, avg, ui.ratingCount))
                } else {
                    FrText(text = resolve(FeedStringKey.NoVotesYet))
                }
            }
            FrText(text = ui.dishName, modifier = Modifier.padding(top = Spacing.sm))
            if (ui.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = ui.photoUrl,
                    contentDescription = ui.dishName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(Spacing.sm)),
                )
            }
            if (ui.tags.isNotEmpty()) {
                FrTagChipRow(
                    tags = ui.tags,
                    selected = emptySet(),
                    onToggle = {},
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            if (ui.votes.isNotEmpty()) {
                FrText(text = resolve(FeedStringKey.VotesHeading), modifier = Modifier.padding(top = Spacing.sm))
                ui.votes.forEach { v ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs),
                    ) {
                        FrText(text = resolve(FeedStringKey.VoterScore, v.raterName, v.score))
                    }
                }
            }
            when {
                ui.viewerRating != null -> {
                    FrText(
                        text = resolve(FeedStringKey.YourVote, ui.viewerRating),
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                ui.canRate -> {
                    FrText(
                        text = resolve(FeedStringKey.RateThisMeal),
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    val minotauroLabel = resolve(FeedStringKey.MinotauroLabel)
                    FrStarRatingPicker(
                        onSelect = { score -> onRate(ui.mealId, score) },
                        modifier = Modifier.padding(top = Spacing.xs),
                        starLabel = { i -> if (i == 6) minotauroLabel else i.toString() },
                    )
                }
                else -> Unit
            }
        }
    }
}
