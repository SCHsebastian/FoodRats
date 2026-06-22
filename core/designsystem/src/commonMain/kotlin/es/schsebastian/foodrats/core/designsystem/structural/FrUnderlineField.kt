package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrFontFamily
import es.schsebastian.foodrats.core.designsystem.tokens.Motion

/**
 * The structural zero-box text field — **underline only**, no container, no fill, no border. Extreme
 * contrast: a 10sp uppercase wide-tracked label sits over an oversized 18sp SemiBold input that writes
 * straight onto the media floor. The sole edge is a 1.5dp bottom line that lights up to olive
 * (`colorScheme.primary`) on focus and recedes to a 45%-foreground hairline at rest.
 *
 * @param onMedia set when the field writes directly over a real photo / `dish*` media floor (e.g. the
 *   composer or the delete-gate), which stays dark-scrimmed in BOTH themes — forces white content so
 *   the input/label/placeholder stay legible in light mode instead of flipping to dark ink.
 */
@Composable
fun FrUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    onMedia: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val contentColor = if (onMedia) StructuralColors.onMedia else StructuralColors.foreground

    // Own the editing buffer locally as a TextFieldValue. The hoisted `value` is a plain String, and
    // the String overload of BasicTextField rebuilds a fresh TextFieldValue — dropping the IME
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

    val underlineColor by animateColorAsState(
        targetValue = when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> contentColor.copy(alpha = 0.45f)
        },
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "underlineColor",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            FrText(
                text = label,
                color = when {
                    isError -> scheme.error
                    onMedia -> contentColor.copy(alpha = 0.7f)
                    else -> scheme.onSurfaceVariant
                },
                style = TextStyle(
                    fontFamily = LocalFrFontFamily.current,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.18.em,
                ),
            )
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                if (newValue.text != value) onValueChange(newValue.text)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(
                color = contentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = LocalFrFontFamily.current,
            ),
            cursorBrush = SolidColor(scheme.primary),
            interactionSource = interaction,
            decorationBox = { innerTextField ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            FrText(
                                text = placeholder,
                                color = contentColor.copy(alpha = 0.4f),
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = LocalFrFontFamily.current,
                                ),
                            )
                        }
                        innerTextField()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(underlineColor),
                    )
                }
            },
        )
    }
}

@FrPreview
@Composable
private fun FrUnderlineFieldPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.stageFloor).padding(24.dp)) {
            var value by remember { mutableStateOf("Saturday Brunch") }
            FrUnderlineField(
                value = value,
                onValueChange = { value = it },
                label = "CREW NAME",
            )
        }
    }
}
