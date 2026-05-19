package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarWithName
import es.schsebastian.foodrats.core.designsystem.molecules.FrScoreBadge
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrFeedLayout(
    dayHeader: @Composable () -> Unit,
    list: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        dayHeader()
        list()
    }
}

@FrPreview
@Composable
private fun FrFeedLayoutPreview() {
    FrPreviewLightDark {
        FrFeedLayout(
            modifier = Modifier.fillMaxWidth(),
            dayHeader = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FrIconButton(icon = FrIcons.ChevronLeft, onClick = {}, contentDescription = "Prev")
                    Text("Tue · 2026-05-19", style = MaterialTheme.typography.titleMedium)
                    FrIconButton(icon = FrIcons.ChevronRight, onClick = {}, contentDescription = "Next")
                }
            },
            list = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sebastián" to 8, "Anika" to 9, "Reggie" to 6).forEach { (name, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            FrAvatarWithName(initials = name.take(2), name = name)
                            FrScoreBadge(score = score)
                        }
                    }
                }
            },
        )
    }
}
