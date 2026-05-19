package es.schsebastian.foodrats.core.designsystem.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

@Composable
fun FrFormLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.padding(Spacing.md)) { content() }
}

@FrPreview
@Composable
private fun FrFormLayoutPreview() {
    FrPreviewLightDark {
        FrFormLayout(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FrLabeledTextField(
                    label = "Crew name",
                    value = "Rats of Tuesday",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                FrLabeledTextField(
                    label = "Invite code",
                    value = "RATS-42",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                FrButton(label = "Create crew", onClick = {})
            }
        }
    }
}
