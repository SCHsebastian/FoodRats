package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import es.schsebastian.foodrats.core.designsystem.structural.LocalStructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.structuralDarkColors
import es.schsebastian.foodrats.core.designsystem.structural.structuralLightColors

@Composable
fun FoodRatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: FrAccent = FrAccent.Ember,
    minotaur: Boolean = false,
    onMinotaurToggle: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) FoodRatsDarkColors else FoodRatsLightColors
    val colors = baseColors.applyAccent(accent, darkTheme)
    val semantic = if (darkTheme) FoodRatsDarkSemanticColors else FoodRatsLightSemanticColors
    val structural = if (darkTheme) structuralDarkColors() else structuralLightColors()
    val fontFamily = rememberFrFontFamily()
    val typography = rememberFoodRatsTypography(fontFamily)
    var minotaurOn by rememberSaveable(minotaur) { mutableStateOf(minotaur) }
    CompositionLocalProvider(
        LocalFrSemanticColors provides semantic,
        LocalStructuralColors provides structural,
        LocalFrFontFamily provides fontFamily,
        LocalMinotaurMode provides minotaurOn,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = FoodRatsShapes,
        ) {
            Box(
                modifier = Modifier.minotaurUnlockGesture {
                    minotaurOn = !minotaurOn
                    onMinotaurToggle(minotaurOn)
                },
            ) {
                content()
            }
        }
    }
}
