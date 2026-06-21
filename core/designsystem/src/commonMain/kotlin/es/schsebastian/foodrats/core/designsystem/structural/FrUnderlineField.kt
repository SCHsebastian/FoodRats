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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val underlineColor by animateColorAsState(
        targetValue = if (focused) scheme.primary else StructuralColors.foreground.copy(alpha = 0.45f),
        animationSpec = tween(Motion.quick, easing = Motion.Standard),
        label = "underlineColor",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            FrText(
                text = label,
                color = scheme.onSurfaceVariant,
                style = TextStyle(
                    fontFamily = LocalFrFontFamily.current,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.18.em,
                ),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = StructuralColors.foreground,
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
                                color = StructuralColors.foreground.copy(alpha = 0.4f),
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
