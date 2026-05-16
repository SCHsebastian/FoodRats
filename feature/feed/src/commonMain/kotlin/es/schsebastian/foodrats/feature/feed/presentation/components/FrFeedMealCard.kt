package es.schsebastian.foodrats.feature.feed.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            // Photo rendering: a real implementation calls Coil-Compose-Multiplatform's AsyncImage.
            // Until image-loading is wired (see App Wiring plan §"Image loading"), render the URL
            // as text so the screen has something to show.
            FrText(text = ui.photoUrl, modifier = Modifier.padding(top = Spacing.xs))
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
