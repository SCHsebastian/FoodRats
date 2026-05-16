package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            FrText(text = label)
            FrText(text = value, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}
