package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrAvatar
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrAvatarWithName(initials: String, name: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FrAvatar(initials = initials)
        FrText(text = name, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@FrPreview
@Composable
private fun FrAvatarWithNamePreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrAvatarWithName(initials = "SC", name = "Sebastián Cardona")
            FrAvatarWithName(initials = "AN", name = "Anika")
            FrAvatarWithName(initials = "RK", name = "Reggie K. (long-display-name)")
        }
    }
}
