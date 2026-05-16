package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun FoodRatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FoodRatsDarkColors else FoodRatsLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = FoodRatsTypography,
        shapes = FoodRatsShapes,
        content = content,
    )
}
