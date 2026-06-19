package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrLabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    isError: Boolean = false,
) {
    Column(modifier = modifier) {
        FrText(text = label)
        // The label is drawn above as a sibling, so the field itself has no Material label and
        // would read as an unnamed edit box. Mirror the label into the field's accessible name
        // (WCAG 4.1.2 / 3.3.2) without rendering a second visible label.
        FrTextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            modifier = Modifier.semantics { contentDescription = label },
        )
        helper?.let { FrText(text = it) }
    }
}

@FrPreview
@Composable
private fun FrLabeledTextFieldPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FrLabeledTextField(
                label = "Dish name",
                value = "",
                onValueChange = {},
                helper = "What did you eat?",
            )
            FrLabeledTextField(
                label = "Crew code",
                value = "RATS-42",
                onValueChange = {},
            )
            FrLabeledTextField(
                label = "Email",
                value = "not-an-email",
                onValueChange = {},
                helper = "Enter a valid email address.",
                isError = true,
            )
        }
    }
}
