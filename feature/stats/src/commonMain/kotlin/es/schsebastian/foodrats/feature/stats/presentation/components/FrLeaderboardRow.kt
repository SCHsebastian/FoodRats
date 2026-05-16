package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.feature.stats.domain.model.MemberAverage

@Composable
fun FrLeaderboardRow(entry: MemberAverage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // FrAvatar takes initials; derive from display name.
        FrAvatar(initials = entry.displayName.take(2))
        FrText(text = entry.displayName, modifier = Modifier.padding(end = Spacing.md))
        FrText(text = ((entry.averageScore * 10).toLong().toDouble() / 10).toString())
        FrText(text = "(${entry.postCount})", modifier = Modifier.padding(start = Spacing.sm))
    }
}
