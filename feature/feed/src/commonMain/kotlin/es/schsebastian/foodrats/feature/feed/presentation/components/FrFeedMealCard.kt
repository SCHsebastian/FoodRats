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
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.molecules.FrTagChipRow
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrFeedMealCard(
    ui: FeedMealUi,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(Spacing.md)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // FrAvatarWithName takes initials + name; derive initials from authorName.
                FrAvatarWithName(
                    initials = ui.authorName.take(2).uppercase(),
                    name = ui.authorName,
                )
                FrScoreBadge(score = ui.score)
            }
            FrText(text = ui.dishName, modifier = Modifier.padding(top = Spacing.sm))
            // Meal photo. Coil 3 + the Ktor 3 fetcher loads the image on Android and iOS
            // from ui.photoUrl (Firebase Storage download URL).
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
                // FrTagChipRow requires selected + onToggle; feed cards are read-only so pass
                // an empty selected set and a no-op toggle.
                FrTagChipRow(
                    tags = ui.tags,
                    selected = emptySet(),
                    onToggle = {},
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
