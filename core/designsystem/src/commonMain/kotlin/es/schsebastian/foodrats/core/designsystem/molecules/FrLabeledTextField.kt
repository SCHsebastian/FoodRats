package es.schsebastian.foodrats.core.designsystem.molecules

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField

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
        FrTextField(value = value, onValueChange = onValueChange, isError = isError)
        helper?.let { FrText(text = it) }
    }
}
