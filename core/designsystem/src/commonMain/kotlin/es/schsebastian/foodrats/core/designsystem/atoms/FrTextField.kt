package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.preview.FrPreviewLightDark

@Composable
fun FrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
    )
}

@FrPreview
@Composable
private fun FrTextFieldPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrTextField(value = "", onValueChange = {}, label = "Dish name", placeholder = "Enter dish name")
            FrTextField(value = "Roast chicken", onValueChange = {}, label = "Dish name")
            FrTextField(value = "Bad input", onValueChange = {}, label = "Dish name", isError = true)
            FrTextField(value = "Locked", onValueChange = {}, label = "Dish name", enabled = false)
        }
    }
}
