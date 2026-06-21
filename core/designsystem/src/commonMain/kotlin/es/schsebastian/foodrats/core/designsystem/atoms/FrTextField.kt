package es.schsebastian.foodrats.core.designsystem.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    // Default to sentence auto-capitalization — the right behavior for the prose/name fields that
    // make up most call sites (comment, description, bio, dish/crew/profile names). Fields that must
    // not be transformed (email, password, search, codes, exact-match phrase gates) pass their own
    // KeyboardOptions and so opt out. Capitalization is an IME hint and is independent of the local
    // TextFieldValue editing buffer above.
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    // Own the editing buffer locally as a TextFieldValue. The hoisted `value` is a plain String, and
    // the String overload of OutlinedTextField rebuilds a fresh TextFieldValue — dropping the IME
    // composition span — on every recomposition the hoisted state triggers. With a predictive
    // soft-keyboard (which types via setComposingText), that tears the composing session each
    // keystroke and the cursor jumps to the start, so words appear to type backwards. (commitText
    // input — paste, adb input text — never composes, which is why only the on-screen keyboard hit
    // it.) Keeping the TextFieldValue (cursor + composition) local and only reconciling the text on
    // GENUINE external changes keeps the composing session intact during normal typing.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // React only when the hoisted value actually changes between recompositions (clear-on-send,
    // prefill, validation rewrite, truncation). The echo of our own keystroke arrives as
    // value == fieldValue.text, so it's a no-op; a stale/lagging echo leaves `value` unchanged and
    // never fires here — neither can roll the cursor back mid-typing.
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            if (newValue.text != value) onValueChange(newValue.text)
        },
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}

@FrPreview
@Composable
private fun FrTextFieldPreview() {
    FrPreviewLightDark {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrTextField(value = "", onValueChange = {}, label = "Dish name", placeholder = "Enter dish name")
            FrTextField(value = "Roast chicken", onValueChange = {}, label = "Dish name")
            FrTextField(
                value = "you@example.com",
                onValueChange = {},
                label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            FrTextField(
                value = "hunter2",
                onValueChange = {},
                label = "Password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            FrTextField(value = "Bad input", onValueChange = {}, label = "Dish name", isError = true, supportingText = "Required")
            FrTextField(value = "Locked", onValueChange = {}, label = "Dish name", enabled = false)
        }
    }
}
