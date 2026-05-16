package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val LightPrimary   = Color(0xFFE6552F)
private val LightSecondary = Color(0xFF2F8F4A)

internal val FoodRatsLightColors = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
)

internal val FoodRatsDarkColors = darkColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
)
