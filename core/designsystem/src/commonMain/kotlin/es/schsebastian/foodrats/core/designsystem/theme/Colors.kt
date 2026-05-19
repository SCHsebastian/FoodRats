package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — Avocado + Citrus.
// Avocado primary anchors the healthy identity; honey-citrus secondary highlights
// streaks, scores, and badges; berry tertiary is reserved for rare celebration moments.
private val AvocadoLight        = Color(0xFF3FB950)
private val AvocadoLightOn      = Color(0xFFFFFFFF)
private val AvocadoContainer    = Color(0xFFC5EFC9)
private val AvocadoOnContainer  = Color(0xFF06310C)

private val CitrusLight         = Color(0xFFFFB020)
private val CitrusOn            = Color(0xFF2C1A00)
private val CitrusContainer     = Color(0xFFFFE9BD)
private val CitrusOnContainer   = Color(0xFF2C1A00)

private val BerryLight          = Color(0xFFE84B6A)
private val BerryOn             = Color(0xFFFFFFFF)
private val BerryContainer      = Color(0xFFFFD9DF)
private val BerryOnContainer    = Color(0xFF410012)

private val ErrorLight          = Color(0xFFBA1A1A)
private val ErrorOnLight        = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val ErrorOnContainerLgt = Color(0xFF410002)

private val WarmCream           = Color(0xFFFBFAF6)
private val OnWarmCream         = Color(0xFF1A1C18)
private val WarmCreamElevated   = Color(0xFFF2EFE6)
private val OnWarmCreamElevated = Color(0xFF44483D)
private val OutlineLight        = Color(0xFF74796C)
private val OutlineVariantLight = Color(0xFFC4C8B7)
private val InverseSurfaceLight = Color(0xFF2F312C)
private val InverseOnLight      = Color(0xFFF1F1E9)

private val AvocadoDark         = Color(0xFF6EDC78)
private val AvocadoDarkOn       = Color(0xFF003910)
private val AvocadoContainerD   = Color(0xFF1F5527)
private val AvocadoOnContainerD = Color(0xFFC5EFC9)

private val CitrusDark          = Color(0xFFFFCB78)
private val CitrusOnDark        = Color(0xFF432B00)
private val CitrusContainerD    = Color(0xFF5E3F00)
private val CitrusOnContainerD  = Color(0xFFFFE0B2)

private val BerryDark           = Color(0xFFFFB1BD)
private val BerryOnDark         = Color(0xFF5F1124)
private val BerryContainerD     = Color(0xFF82253A)
private val BerryOnContainerD   = Color(0xFFFFD9DF)

private val ErrorDark           = Color(0xFFFFB4AB)
private val ErrorOnDark         = Color(0xFF690005)
private val ErrorContainerDark  = Color(0xFF93000A)
private val ErrorOnContainerDk  = Color(0xFFFFDAD6)

private val ForestNight         = Color(0xFF0F1411)
private val OnForestNight       = Color(0xFFE3E3DC)
private val ForestNightElevated = Color(0xFF1B2520)
private val OnForestNightElev   = Color(0xFFC4C8BB)
private val OutlineDark         = Color(0xFF8E9387)
private val OutlineVariantDark  = Color(0xFF44483D)

internal val FoodRatsLightColors = lightColorScheme(
    primary               = AvocadoLight,
    onPrimary             = AvocadoLightOn,
    primaryContainer      = AvocadoContainer,
    onPrimaryContainer    = AvocadoOnContainer,
    secondary             = CitrusLight,
    onSecondary           = CitrusOn,
    secondaryContainer    = CitrusContainer,
    onSecondaryContainer  = CitrusOnContainer,
    tertiary              = BerryLight,
    onTertiary            = BerryOn,
    tertiaryContainer     = BerryContainer,
    onTertiaryContainer   = BerryOnContainer,
    error                 = ErrorLight,
    onError               = ErrorOnLight,
    errorContainer        = ErrorContainerLight,
    onErrorContainer      = ErrorOnContainerLgt,
    background            = WarmCream,
    onBackground          = OnWarmCream,
    surface               = WarmCream,
    onSurface             = OnWarmCream,
    surfaceVariant        = WarmCreamElevated,
    onSurfaceVariant      = OnWarmCreamElevated,
    surfaceTint           = AvocadoLight,
    outline               = OutlineLight,
    outlineVariant        = OutlineVariantLight,
    inverseSurface        = InverseSurfaceLight,
    inverseOnSurface      = InverseOnLight,
    inversePrimary        = AvocadoDark,
    scrim                 = Color.Black,
)

internal val FoodRatsDarkColors = darkColorScheme(
    primary               = AvocadoDark,
    onPrimary             = AvocadoDarkOn,
    primaryContainer      = AvocadoContainerD,
    onPrimaryContainer    = AvocadoOnContainerD,
    secondary             = CitrusDark,
    onSecondary           = CitrusOnDark,
    secondaryContainer    = CitrusContainerD,
    onSecondaryContainer  = CitrusOnContainerD,
    tertiary              = BerryDark,
    onTertiary            = BerryOnDark,
    tertiaryContainer     = BerryContainerD,
    onTertiaryContainer   = BerryOnContainerD,
    error                 = ErrorDark,
    onError               = ErrorOnDark,
    errorContainer        = ErrorContainerDark,
    onErrorContainer      = ErrorOnContainerDk,
    background            = ForestNight,
    onBackground          = OnForestNight,
    surface               = ForestNight,
    onSurface             = OnForestNight,
    surfaceVariant        = ForestNightElevated,
    onSurfaceVariant      = OnForestNightElev,
    surfaceTint           = AvocadoDark,
    outline               = OutlineDark,
    outlineVariant        = OutlineVariantDark,
    inverseSurface        = OnForestNight,
    inverseOnSurface      = InverseSurfaceLight,
    inversePrimary        = AvocadoLight,
    scrim                 = Color.Black,
)
