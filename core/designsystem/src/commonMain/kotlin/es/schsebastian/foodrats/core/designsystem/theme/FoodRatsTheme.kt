package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun FoodRatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FoodRatsDarkColors else FoodRatsLightColors
    val semantic = if (darkTheme) FoodRatsDarkSemanticColors else FoodRatsLightSemanticColors
    val fontFamily = rememberFrFontFamily()
    val typography = rememberFoodRatsTypography(fontFamily)
    CompositionLocalProvider(
        LocalFrSemanticColors provides semantic,
        LocalFrFontFamily provides fontFamily,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = FoodRatsShapes,
            content = content,
        )
    }
}
